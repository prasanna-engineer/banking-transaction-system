package com.example.banking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.transfer.TransferRequest;
import com.example.banking.dto.transfer.TransferResponse;
import com.example.banking.entity.BankAccount;
import com.example.banking.entity.BankTransaction;
import com.example.banking.entity.User;
import com.example.banking.exception.ApiException;
import com.example.banking.repository.BankAccountRepository;
import com.example.banking.repository.BankTransactionRepository;

/**
 * Moves money between two accounts.
 *
 * Locking strategy: pessimistic row locks (SELECT ... FOR UPDATE) on both accounts, always taken
 * in ascending id order. Concurrent transfers touching the same account therefore run one after
 * another instead of overwriting each other's balance, and two opposite transfers (A->B, B->A)
 * cannot deadlock because both wait for the lower id first. The @Version column on BankAccount
 * is a second line of defence: an update based on stale data would fail instead of losing money.
 */
@Service
@EnableConfigurationProperties(TransferProperties.class)
public class TransferService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final String REFERENCE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private final BankAccountRepository accountRepository;

	private final BankTransactionRepository transactionRepository;

	private final CurrentUserService currentUserService;

	private final AuditService auditService;

	private final TransferProperties properties;

	public TransferService(BankAccountRepository accountRepository, BankTransactionRepository transactionRepository,
			CurrentUserService currentUserService, AuditService auditService, TransferProperties properties) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
		this.currentUserService = currentUserService;
		this.auditService = auditService;
		this.properties = properties;
	}

	/**
	 * Everything below runs in ONE database transaction: the debit, the credit, the transaction
	 * record and the audit record are committed together, or - if anything throws - none of them are.
	 */
	@Transactional
	public TransferResponse transfer(Long currentUserId, TransferRequest request) {
		User user = currentUserService.requireActiveUser(currentUserId);
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
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("reference", tx.getReference());
		metadata.put("sourceAccountId", sourceId);
		metadata.put("destinationAccountId", destinationId);
		metadata.put("amount", amount.toPlainString());
		metadata.put("currency", tx.getCurrency());
		auditService.record(user, "TRANSFER_COMPLETED", "TRANSACTION", tx.getId(), metadata);

		return TransferResponse.from(tx);
	}

	private BankAccount lockAccount(Long id) {
		return accountRepository.findByIdForUpdate(id).orElseThrow(TransferService::accountNotFound);
	}

	private static ApiException accountNotFound() {
		return ApiException.notFound("ACCOUNT_NOT_FOUND", "Account not found.");
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

}
