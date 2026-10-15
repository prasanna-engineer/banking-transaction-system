package com.example.banking.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.payee.CreatePayeeRequest;
import com.example.banking.dto.payee.PayeeResponse;
import com.example.banking.entity.BankAccount;
import com.example.banking.entity.Payee;
import com.example.banking.entity.User;
import com.example.banking.exception.ApiException;
import com.example.banking.repository.BankAccountRepository;
import com.example.banking.repository.PayeeRepository;

@Service
public class PayeeService {

	private final PayeeRepository payeeRepository;

	private final BankAccountRepository accountRepository;

	private final CurrentUserService currentUserService;

	private final AuditService auditService;

	public PayeeService(PayeeRepository payeeRepository, BankAccountRepository accountRepository,
			CurrentUserService currentUserService, AuditService auditService) {
		this.payeeRepository = payeeRepository;
		this.accountRepository = accountRepository;
		this.currentUserService = currentUserService;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<PayeeResponse> list(Long currentUserId) {
		User user = currentUserService.requireActiveUser(currentUserId);
		return payeeRepository.findByUserIdOrderByNicknameAsc(user.getId()).stream().map(PayeeResponse::from).toList();
	}

	/**
	 * Like a bank's "add beneficiary": the account must exist and be active, must not be one of the
	 * caller's own accounts (those are picked directly), and may be saved only once.
	 */
	@Transactional
	public PayeeResponse create(Long currentUserId, CreatePayeeRequest request) {
		User user = currentUserService.requireActiveUser(currentUserId);
		String accountNumber = request.accountNumber().trim();
		BankAccount account = accountRepository.findByAccountNumber(accountNumber)
			.filter(found -> found.isActive())
			.orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND",
					"No active account with this number was found."));
		if (account.getUser().getId().equals(user.getId())) {
			throw ApiException.badRequest("OWN_ACCOUNT",
					"This is one of your own accounts - choose it directly when transferring.");
		}
		if (payeeRepository.existsByUserIdAndAccountNumber(user.getId(), accountNumber)) {
			throw new ApiException(HttpStatus.CONFLICT, "PAYEE_EXISTS", "This account is already in your payees.");
		}
		Payee payee = payeeRepository.save(new Payee(user, request.nickname().trim(), accountNumber));
		auditService.record(user, "PAYEE_ADDED", "PAYEE", payee.getId(), metadata(payee));
		return PayeeResponse.from(payee);
	}

	@Transactional
	public void delete(Long currentUserId, Long payeeId) {
		User user = currentUserService.requireActiveUser(currentUserId);
		Payee payee = payeeRepository.findByIdAndUserId(payeeId, user.getId())
			.orElseThrow(() -> ApiException.notFound("PAYEE_NOT_FOUND", "Payee not found."));
		auditService.record(user, "PAYEE_REMOVED", "PAYEE", payee.getId(), metadata(payee));
		payeeRepository.delete(payee);
	}

	private static Map<String, Object> metadata(Payee payee) {
		String number = payee.getAccountNumber();
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("nickname", payee.getNickname());
		metadata.put("accountNumber", "••••" + number.substring(number.length() - 4));
		return metadata;
	}

}
