package com.example.banking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.banking.dto.transfer.TransferRequest;
import com.example.banking.dto.transfer.TransferResponse;
import com.example.banking.entity.BankAccount;
import com.example.banking.entity.BankTransaction;
import com.example.banking.entity.IdempotencyRecord;
import com.example.banking.entity.User;
import com.example.banking.exception.ApiException;
import com.example.banking.repository.BankAccountRepository;
import com.example.banking.repository.BankTransactionRepository;
import com.example.banking.repository.IdempotencyRecordRepository;

/**
 * Moves money between two accounts.
 *
 * Locking strategy: pessimistic row locks (SELECT ... FOR UPDATE) on both accounts, always taken
 * in ascending id order. Concurrent transfers touching the same account therefore run one after
 * another instead of overwriting each other's balance, and two opposite transfers (A->B, B->A)
 * cannot deadlock because both wait for the lower id first. The @Version column on BankAccount
 * is a second line of defence: an update based on stale data would fail instead of losing money.
 *
 * Idempotency: with an Idempotency-Key, a retried request returns the original result instead of
 * moving money again. See {@link #transfer(Long, TransferRequest, String)}.
 */
@Service
@EnableConfigurationProperties(TransferProperties.class)
public class TransferService {

	private static final Logger log = LoggerFactory.getLogger(TransferService.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final String REFERENCE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	// UUIDs and similar random strings; no spaces or other characters that could be abused in logs.
	private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,100}");

	private final BankAccountRepository accountRepository;

	private final BankTransactionRepository transactionRepository;

	private final IdempotencyRecordRepository idempotencyRepository;

	private final CurrentUserService currentUserService;

	private final AuditService auditService;

	private final TransferProperties properties;

	private final TransactionTemplate transactionTemplate;

	private final TransactionTemplate readOnlyTransactionTemplate;

	public TransferService(BankAccountRepository accountRepository, BankTransactionRepository transactionRepository,
			IdempotencyRecordRepository idempotencyRepository, CurrentUserService currentUserService,
			AuditService auditService, TransferProperties properties, PlatformTransactionManager transactionManager) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.currentUserService = currentUserService;
		this.auditService = auditService;
		this.properties = properties;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
		this.readOnlyTransactionTemplate.setReadOnly(true);
	}

	/** A transfer without an idempotency key (used by tests and internal callers). */
	public TransferResponse transfer(Long currentUserId, TransferRequest request) {
		return transfer(currentUserId, request, null).response();
	}

	/**
	 * With a key: (1) if this user already used the key, replay the stored result - or reject with 409
	 * if the request data differs; (2) otherwise run the transfer, recording the key in the SAME
	 * database transaction. If two identical requests race, the database's unique index lets only one
	 * commit; the other is rolled back (no money moved) and then replays the winner's result.
	 *
	 * Not @Transactional itself: it starts its own transactions so it can recover after one is rolled back.
	 */
	public TransferResult transfer(Long currentUserId, TransferRequest request, String idempotencyKey) {
		if (idempotencyKey == null) {
			return new TransferResult(transactionTemplate.execute(status -> doTransfer(currentUserId, request, null)),
					false);
		}
		if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
			throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY",
					"Idempotency-Key must be 8-100 letters, digits, '-' or '_' (a UUID is ideal).");
		}
		String requestHash = hash(request);

		Optional<TransferResult> previous = findPrevious(currentUserId, idempotencyKey, requestHash);
		if (previous.isPresent()) {
			return previous.get();
		}
		try {
			return new TransferResult(
					transactionTemplate
						.execute(status -> doTransfer(currentUserId, request, new KeyClaim(idempotencyKey, requestHash))),
					false);
		}
		catch (DataIntegrityViolationException ex) {
			// Most likely a simultaneous request with the same key committed first. Our transaction was
			// rolled back, so nothing moved; answer with the other request's result.
			return findPrevious(currentUserId, idempotencyKey, requestHash).orElseThrow(() -> ex);
		}
	}

	private Optional<TransferResult> findPrevious(Long userId, String key, String requestHash) {
		return readOnlyTransactionTemplate
			.execute(status -> idempotencyRepository.findByUserIdAndIdempotencyKey(userId, key).map(record -> {
				if (!record.getRequestHash().equals(requestHash)) {
					throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
							"This Idempotency-Key was already used for a different transfer.");
				}
				if (record.getTransaction() == null) {
					// Only visible inside the transaction that is still processing it; never after a commit.
					throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
							"A request with this Idempotency-Key is still being processed. Please try again.");
				}
				log.info("Replaying transfer {} for repeated Idempotency-Key", record.getTransaction().getReference());
				return new TransferResult(TransferResponse.from(record.getTransaction()), true);
			}));
	}

	/**
	 * Everything in here runs in ONE database transaction: the idempotency key, the debit, the credit,
	 * the transaction record and the audit record are committed together, or - if anything throws - none are.
	 */
	private TransferResponse doTransfer(Long currentUserId, TransferRequest request, KeyClaim keyClaim) {
		User user = currentUserService.requireActiveUser(currentUserId);

		// 0. Claim the idempotency key first. A second request with the same key now waits on the unique
		// index until this transaction ends, and then fails instead of moving money a second time.
		IdempotencyRecord keyRecord = keyClaim == null ? null
				: idempotencyRepository.saveAndFlush(new IdempotencyRecord(user, keyClaim.key(), keyClaim.requestHash()));

		// Bean Validation already rejected <= 0 and > 2 decimals; normalise to exactly 2 (never rounds).
		BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
		Long sourceId = request.sourceAccountId();
		Long destinationId = request.destinationAccountId() != null ? request.destinationAccountId()
				: accountRepository.findIdByAccountNumber(request.destinationAccountNumber())
					.orElseThrow(TransferService::accountNotFound);

		// 1. Cheap checks first, before touching any locks.
		if (amount.compareTo(properties.maxAmount()) > 0) {
			throw ApiException.badRequest("AMOUNT_LIMIT_EXCEEDED",
					"The amount exceeds the demo limit of " + properties.maxAmount().toPlainString() + ".");
		}
		if (sourceId.equals(destinationId)) {
			throw ApiException.badRequest("SAME_ACCOUNT", "Source and destination accounts must be different.");
		}
		// The caller must own the source account. Someone else's account gets the same 404 as a missing one.
		if (!accountRepository.existsByIdAndUserId(sourceId, user.getId())) {
			throw accountNotFound();
		}

		// 2. Lock both rows in a consistent order (lowest id first) to avoid deadlocks.
		BankAccount first = lockAccount(Math.min(sourceId, destinationId));
		BankAccount second = lockAccount(Math.max(sourceId, destinationId));
		BankAccount source = first.getId().equals(sourceId) ? first : second;
		BankAccount destination = source == first ? second : first;

		// 3. Re-check everything against the locked, current state of the rows.
		if (!source.isActive() || !destination.isActive()) {
			throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_ACTIVE", "Both accounts must be active.");
		}
		if (!source.getCurrency().equals(destination.getCurrency())) {
			throw ApiException.badRequest("CURRENCY_MISMATCH", "Both accounts must use the same currency.");
		}
		if (source.getBalance().compareTo(amount) < 0) {
			throw ApiException.badRequest("INSUFFICIENT_FUNDS", "The source account has insufficient funds.");
		}

		// 4. Move the money. No save() needed: Hibernate writes the changed balances at commit.
		source.debit(amount);
		destination.credit(amount);

		// 5. Record what happened, in the same transaction.
		BankTransaction tx = transactionRepository.save(BankTransaction.transfer(newReference(), source, destination,
				amount, blankToNull(request.description()), user));
		if (keyRecord != null) {
			keyRecord.complete(tx);
		}
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("reference", tx.getReference());
		metadata.put("sourceAccountId", sourceId);
		metadata.put("destinationAccountId", destinationId);
		metadata.put("amount", amount.toPlainString());
		metadata.put("currency", tx.getCurrency());
		auditService.record(user, "TRANSFER_COMPLETED", "TRANSACTION", tx.getId(), metadata);
		log.info("Transfer {} completed: {} {} from account {} to account {} by user {}", tx.getReference(),
				amount.toPlainString(), tx.getCurrency(), sourceId, destinationId, user.getId());

		return TransferResponse.from(tx);
	}

	private BankAccount lockAccount(Long id) {
		return accountRepository.findByIdForUpdate(id).orElseThrow(TransferService::accountNotFound);
	}

	private static ApiException accountNotFound() {
		return ApiException.notFound("ACCOUNT_NOT_FOUND", "Account not found.");
	}

	/** Fingerprint of the request data. "100", "100.0" and "100.00" count as the same amount. */
	private static String hash(TransferRequest request) {
		String canonical = String.join("|", String.valueOf(request.sourceAccountId()),
				String.valueOf(request.destinationAccountId()), String.valueOf(request.destinationAccountNumber()),
				request.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
				String.valueOf(blankToNull(request.description())));
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available in the JDK", ex);
		}
	}

	/** e.g. TRX-20261014-7K3QM9XP2A. Uniqueness is also guaranteed by the database's unique index. */
	private static String newReference() {
		StringBuilder random = new StringBuilder(10);
		for (int i = 0; i < 10; i++) {
			random.append(REFERENCE_CHARS.charAt(RANDOM.nextInt(REFERENCE_CHARS.length())));
		}
		return "TRX-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + random;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	/** replayed = true when the response comes from an earlier request with the same Idempotency-Key. */
	public record TransferResult(TransferResponse response, boolean replayed) {
	}

	private record KeyClaim(String key, String requestHash) {
	}

}
