package com.example.banking.service;

import static com.example.banking.repository.TransactionSpecifications.createdAtOrAfter;
import static com.example.banking.repository.TransactionSpecifications.createdBefore;
import static com.example.banking.repository.TransactionSpecifications.hasStatus;
import static com.example.banking.repository.TransactionSpecifications.hasType;
import static com.example.banking.repository.TransactionSpecifications.involvesAccount;
import static com.example.banking.repository.TransactionSpecifications.involvesUser;

import java.time.ZoneOffset;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.PageResponse;
import com.example.banking.dto.transaction.TransactionFilter;
import com.example.banking.dto.transaction.TransactionResponse;
import com.example.banking.entity.BankTransaction;
import com.example.banking.entity.User;
import com.example.banking.exception.ApiException;
import com.example.banking.repository.BankTransactionRepository;

@Service
public class TransactionService {

	static final int MAX_PAGE_SIZE = 100;

	// Only these fields may be sorted on; anything else from the URL is rejected.
	private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "amount");

	private final BankTransactionRepository transactionRepository;

	private final AccountService accountService;

	private final CurrentUserService currentUserService;

	public TransactionService(BankTransactionRepository transactionRepository, AccountService accountService,
			CurrentUserService currentUserService) {
		this.transactionRepository = transactionRepository;
		this.accountService = accountService;
		this.currentUserService = currentUserService;
	}

	@Transactional(readOnly = true)
	public PageResponse<TransactionResponse> search(Long currentUserId, TransactionFilter filter, int page, int size,
			String sort) {
		User user = currentUserService.requireActiveUser(currentUserId);
		if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
			throw ApiException.badRequest("INVALID_DATE_RANGE", "'from' must not be after 'to'.");
		}

		// Like chaining ->when(...) on a Laravel query builder: each filter adds one condition.
		Specification<BankTransaction> spec = Specification.unrestricted();
		if (!user.isAdmin()) {
			spec = spec.and(involvesUser(user.getId()));
		}
		if (filter.accountId() != null) {
			accountService.findAccessibleAccount(user, filter.accountId());
			spec = spec.and(involvesAccount(filter.accountId()));
		}
		if (filter.type() != null) {
			spec = spec.and(hasType(filter.type()));
		}
		if (filter.status() != null) {
			spec = spec.and(hasStatus(filter.status()));
		}
		if (filter.from() != null) {
			spec = spec.and(createdAtOrAfter(filter.from().atStartOfDay().toInstant(ZoneOffset.UTC)));
		}
		if (filter.to() != null) {
			spec = spec.and(createdBefore(filter.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
		}

		return PageResponse.from(transactionRepository.findAll(spec, toPageable(page, size, sort)),
				TransactionResponse::from);
	}

	@Transactional(readOnly = true)
	public TransactionResponse getTransaction(Long currentUserId, Long transactionId) {
		User user = currentUserService.requireActiveUser(currentUserId);
		return transactionRepository.findById(transactionId)
			.filter(tx -> user.isAdmin() || tx.involvesUser(user.getId()))
			.map(TransactionResponse::from)
			.orElseThrow(() -> ApiException.notFound("TRANSACTION_NOT_FOUND", "Transaction not found."));
	}

	/** "createdAt,desc" -> Pageable. id is always added as a tie-breaker so page boundaries are stable. */
	private static Pageable toPageable(int page, int size, String sort) {
		if (page < 0) {
			throw ApiException.badRequest("INVALID_PAGE", "'page' must be 0 or greater.");
		}
		String[] parts = sort.split(",");
		String field = parts[0].trim();
		String direction = parts.length > 1 ? parts[1].trim() : "desc";
		if (!SORTABLE_FIELDS.contains(field) || parts.length > 2
				|| !(direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc"))) {
			throw ApiException.badRequest("INVALID_SORT", "Sort must be one of " + SORTABLE_FIELDS
					+ " optionally followed by ',asc' or ',desc'.");
		}
		Sort.Direction dir = Sort.Direction.fromString(direction);
		return PageRequest.of(page, Math.clamp(size, 1, MAX_PAGE_SIZE), Sort.by(dir, field).and(Sort.by(dir, "id")));
	}

}
