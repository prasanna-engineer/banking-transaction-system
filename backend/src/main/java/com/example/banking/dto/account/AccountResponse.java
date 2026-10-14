package com.example.banking.dto.account;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.banking.entity.BankAccount;
import com.example.banking.enums.AccountStatus;
import com.example.banking.enums.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;

public record AccountResponse(
		Long id,
		String accountNumber,
		AccountType accountType,
		// Sent as a JSON string ("50000.00") so JavaScript never turns it into an imprecise float.
		@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
		String currency,
		AccountStatus status,
		Instant createdAt) {

	public static AccountResponse from(BankAccount account) {
		return new AccountResponse(account.getId(), account.getAccountNumber(), account.getAccountType(),
				account.getBalance(), account.getCurrency(), account.getStatus(), account.getCreatedAt());
	}

}
