package com.bank.banking.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bank.banking.dto.AccountResponse;
import com.bank.banking.dto.AmountRequest;
import com.bank.banking.dto.CreateAccountRequest;
import com.bank.banking.dto.PageResponse;
import com.bank.banking.dto.TransactionResponse;
import com.bank.banking.service.AccountService;
import com.bank.banking.service.TransactionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final AccountService accountService;

	private final TransactionService transactionService;

	public AccountController(AccountService accountService, TransactionService transactionService) {
		this.accountService = accountService;
		this.transactionService = transactionService;
	}

	@PostMapping
	public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
		AccountResponse account = accountService.create(request);
		return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.accountNumber())).body(account);
	}

	@GetMapping
	public List<AccountResponse> list() {
		return accountService.findAll();
	}

	@GetMapping("/{accountNumber}")
	public AccountResponse get(@PathVariable String accountNumber) {
		return accountService.findByNumber(accountNumber);
	}

	@PatchMapping("/{accountNumber}/close")
	public AccountResponse close(@PathVariable String accountNumber) {
		return accountService.close(accountNumber);
	}

	@PostMapping("/{accountNumber}/deposit")
	public TransactionResponse deposit(@PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
		return transactionService.deposit(accountNumber, request.amount(), request.description());
	}

	@PostMapping("/{accountNumber}/withdraw")
	public TransactionResponse withdraw(@PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
		return transactionService.withdraw(accountNumber, request.amount(), request.description());
	}

	@GetMapping("/{accountNumber}/transactions")
	public PageResponse<TransactionResponse> transactions(@PathVariable String accountNumber,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return transactionService.history(accountNumber, page, size);
	}

}
