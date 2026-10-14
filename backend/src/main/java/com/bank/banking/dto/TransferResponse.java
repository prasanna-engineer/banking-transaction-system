package com.bank.banking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
		String reference,
		String fromAccountNumber,
		String toAccountNumber,
		BigDecimal amount,
		BigDecimal fromBalanceAfter,
		Instant createdAt) {
}
