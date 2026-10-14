package com.bank.banking.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.banking.dto.AccountResponse;
import com.bank.banking.dto.CreateAccountRequest;
import com.bank.banking.entity.Account;
import com.bank.banking.exception.ResourceNotFoundException;
import com.bank.banking.repository.AccountRepository;

@Service
public class AccountService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AccountRepository accountRepository;

	private final TransactionService transactionService;

	// Constructor injection: Spring sees this constructor and passes in the beans it needs.
	public AccountService(AccountRepository accountRepository, TransactionService transactionService) {
		this.accountRepository = accountRepository;
		this.transactionService = transactionService;
	}

	@Transactional
	public AccountResponse create(CreateAccountRequest request) {
		Account account = accountRepository.save(new Account(generateAccountNumber(), request.holderName().trim(),
				request.email().trim().toLowerCase(), request.accountType()));

		if (request.initialDeposit() != null && request.initialDeposit().signum() > 0) {
			transactionService.deposit(account.getAccountNumber(), request.initialDeposit(), "Initial deposit");
		}
		return AccountResponse.from(account);
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> findAll() {
		return accountRepository.findAllByOrderByCreatedAtDesc().stream().map(AccountResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public AccountResponse findByNumber(String accountNumber) {
		return AccountResponse.from(getAccount(accountNumber));
	}

	@Transactional
	public AccountResponse close(String accountNumber) {
		Account account = accountRepository.findByAccountNumberForUpdate(accountNumber)
			.orElseThrow(() -> notFound(accountNumber));
		account.close();
		return AccountResponse.from(account);
	}

	private Account getAccount(String accountNumber) {
		return accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> notFound(accountNumber));
	}

	private static ResourceNotFoundException notFound(String accountNumber) {
		return new ResourceNotFoundException("Account " + accountNumber + " not found");
	}

	/** 12 digits starting with "10", retried until unused. */
	private String generateAccountNumber() {
		String number;
		do {
			number = "10" + String.format("%010d", RANDOM.nextLong(10_000_000_000L));
		}
		while (accountRepository.existsByAccountNumber(number));
		return number;
	}

}
