package com.bank.banking.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.banking.dto.PageResponse;
import com.bank.banking.dto.TransactionResponse;
import com.bank.banking.dto.TransferRequest;
import com.bank.banking.dto.TransferResponse;
import com.bank.banking.entity.Account;
import com.bank.banking.entity.Transaction;
import com.bank.banking.entity.TransactionType;
import com.bank.banking.exception.BusinessException;
import com.bank.banking.exception.ResourceNotFoundException;
import com.bank.banking.repository.AccountRepository;
import com.bank.banking.repository.TransactionRepository;

@Service
public class TransactionService {

	private final AccountRepository accountRepository;

	private final TransactionRepository transactionRepository;

	public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
		this.accountRepository = accountRepository;
		this.transactionRepository = transactionRepository;
	}

	@Transactional
	public TransactionResponse deposit(String accountNumber, BigDecimal amount, String description) {
		Account account = lockAccount(accountNumber);
		account.deposit(amount);
		Transaction tx = transactionRepository.save(new Transaction(newReference(), account,
				TransactionType.DEPOSIT, amount, null, orDefault(description, "Deposit")));
		return TransactionResponse.from(tx);
	}

	@Transactional
	public TransactionResponse withdraw(String accountNumber, BigDecimal amount, String description) {
		Account account = lockAccount(accountNumber);
		account.withdraw(amount);
		Transaction tx = transactionRepository.save(new Transaction(newReference(), account,
				TransactionType.WITHDRAWAL, amount, null, orDefault(description, "Withdrawal")));
		return TransactionResponse.from(tx);
	}

	@Transactional
	public TransferResponse transfer(TransferRequest request) {
		String fromNumber = request.fromAccountNumber().trim();
		String toNumber = request.toAccountNumber().trim();
		if (fromNumber.equals(toNumber)) {
			throw new BusinessException("Cannot transfer to the same account");
		}

		// Always lock the two accounts in the same order (smallest number first).
		// Otherwise A->B and B->A running together could each hold one lock and wait forever (deadlock).
		boolean fromFirst = fromNumber.compareTo(toNumber) < 0;
		Account first = lockAccount(fromFirst ? fromNumber : toNumber);
		Account second = lockAccount(fromFirst ? toNumber : fromNumber);
		Account from = fromFirst ? first : second;
		Account to = fromFirst ? second : first;

		BigDecimal amount = request.amount();
		from.withdraw(amount);
		to.deposit(amount);

		String reference = newReference();
		String description = orDefault(request.description(), "Transfer");
		Transaction out = transactionRepository.save(new Transaction(reference, from, TransactionType.TRANSFER_OUT,
				amount, to.getAccountNumber(), description));
		transactionRepository.save(new Transaction(reference, to, TransactionType.TRANSFER_IN, amount,
				from.getAccountNumber(), description));

		// No explicit "update" call: JPA notices the changed balances and writes them at commit.
		return new TransferResponse(reference, from.getAccountNumber(), to.getAccountNumber(), out.getAmount(),
				from.getBalance(), out.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public PageResponse<TransactionResponse> history(String accountNumber, int page, int size) {
		Account account = accountRepository.findByAccountNumber(accountNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Account " + accountNumber + " not found"));
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100));
		return PageResponse.from(transactionRepository.findByAccountOrderByCreatedAtDescIdDesc(account, pageable),
				TransactionResponse::from);
	}

	private Account lockAccount(String accountNumber) {
		return accountRepository.findByAccountNumberForUpdate(accountNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Account " + accountNumber + " not found"));
	}

	private static String newReference() {
		return UUID.randomUUID().toString();
	}

	private static String orDefault(String value, String fallback) {
		return (value == null || value.isBlank()) ? fallback : value.trim();
	}

}
