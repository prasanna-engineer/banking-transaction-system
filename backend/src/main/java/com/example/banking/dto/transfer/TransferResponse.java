package com.example.banking.dto.transfer;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.banking.entity.BankTransaction;
import com.example.banking.enums.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

public record TransferResponse(
		String reference,
		TransactionStatus status,
		Long sourceAccountId,
		Long destinationAccountId,
		@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
		String currency,
		Instant createdAt) {

	public static TransferResponse from(BankTransaction tx) {
		return new TransferResponse(tx.getReference(), tx.getStatus(), tx.getSourceAccount().getId(),
				tx.getDestinationAccount().getId(), tx.getAmount(), tx.getCurrency(), tx.getCreatedAt());
	}

}
