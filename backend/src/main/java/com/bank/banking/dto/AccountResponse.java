package com.bank.banking.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.bank.banking.entity.Account;
import com.bank.banking.entity.AccountStatus;
import com.bank.banking.entity.AccountType;

public record AccountResponse(
		String accountNumber,
		String holderName,
		String email,
		AccountType accountType,
		AccountStatus status,
		BigDecimal balance,
		Instant createdAt) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(account.getAccountNumber(), account.getHolderName(), account.getEmail(),
				account.getType(), account.getStatus(), account.getBalance(), account.getCreatedAt());
	}

}
