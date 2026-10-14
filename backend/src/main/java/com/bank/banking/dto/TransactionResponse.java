package com.bank.banking.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.bank.banking.entity.Transaction;
import com.bank.banking.entity.TransactionType;

public record TransactionResponse(
		Long id,
		String reference,
		TransactionType type,
		BigDecimal amount,
		BigDecimal balanceAfter,
		String counterpartyAccountNumber,
		String description,
		Instant createdAt) {

	public static TransactionResponse from(Transaction tx) {
		return new TransactionResponse(tx.getId(), tx.getReference(), tx.getType(), tx.getAmount(),
				tx.getBalanceAfter(), tx.getCounterpartyAccountNumber(), tx.getDescription(), tx.getCreatedAt());
	}

}
