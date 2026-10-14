package com.example.banking.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.account.AccountResponse;
import com.example.banking.entity.BankAccount;
import com.example.banking.entity.User;
import com.example.banking.exception.ApiException;
import com.example.banking.repository.BankAccountRepository;

@Service
public class AccountService {

	private final BankAccountRepository accountRepository;

	private final CurrentUserService currentUserService;

	public AccountService(BankAccountRepository accountRepository, CurrentUserService currentUserService) {
		this.accountRepository = accountRepository;
		this.currentUserService = currentUserService;
	}

	@Transactional(readOnly = true)
	public List<AccountResponse> listMyAccounts(Long currentUserId) {
		User user = currentUserService.requireActiveUser(currentUserId);
		return accountRepository.findByUserIdOrderByIdAsc(user.getId()).stream().map(AccountResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public AccountResponse getAccount(Long currentUserId, Long accountId) {
		return AccountResponse.from(findAccessibleAccount(currentUserService.requireActiveUser(currentUserId), accountId));
	}

	/**
	 * Admins can read any account; customers only their own. Someone else's account gets the
	 * same 404 as a missing one, so callers cannot discover which account ids exist.
	 */
	BankAccount findAccessibleAccount(User user, Long accountId) {
		return (user.isAdmin() ? accountRepository.findById(accountId)
				: accountRepository.findByIdAndUserId(accountId, user.getId()))
			.orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "Account not found."));
	}

}
