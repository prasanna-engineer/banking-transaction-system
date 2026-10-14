package com.example.banking.dto.transaction;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.banking.entity.BankAccount;
import com.example.banking.entity.BankTransaction;
import com.example.banking.enums.TransactionStatus;
import com.example.banking.enums.TransactionType;
import com.fasterxml.jackson.annotation.JsonFormat;

public record TransactionResponse(
		Long id,
		String reference,
		TransactionType type,
		TransactionStatus status,
		Long sourceAccountId,
		String maskedSourceAccountNumber,
		Long destinationAccountId,
		String maskedDestinationAccountNumber,
		@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
		String currency,
		String description,
		Instant createdAt) {

	public static TransactionResponse from(BankTransaction tx) {
		BankAccount source = tx.getSourceAccount();
		BankAccount destination = tx.getDestinationAccount();
		return new TransactionResponse(tx.getId(), tx.getReference(), tx.getType(), tx.getStatus(),
				source == null ? null : source.getId(), mask(source), destination == null ? null : destination.getId(),
				mask(destination), tx.getAmount(), tx.getCurrency(), tx.getDescription(), tx.getCreatedAt());
	}

	/** "100000000003" -> "********0003": the other party's full account number is never exposed. */
	private static String mask(BankAccount account) {
		if (account == null) {
			return null;
		}
		String number = account.getAccountNumber();
		return "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
	}

}
