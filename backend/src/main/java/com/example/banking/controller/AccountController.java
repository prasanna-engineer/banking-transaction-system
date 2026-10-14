package com.example.banking.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.account.AccountResponse;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.AccountService;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

	private final AccountService accountService;

	public AccountController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping
	public List<AccountResponse> list(@CurrentUserId Long currentUserId) {
		return accountService.listMyAccounts(currentUserId);
	}

	@GetMapping("/{id}")
	public AccountResponse get(@CurrentUserId Long currentUserId, @PathVariable Long id) {
		return accountService.getAccount(currentUserId, id);
	}

}
