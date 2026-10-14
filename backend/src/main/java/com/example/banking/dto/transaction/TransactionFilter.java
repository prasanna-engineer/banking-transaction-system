package com.example.banking.dto.transaction;

import java.time.LocalDate;

import com.example.banking.enums.TransactionStatus;
import com.example.banking.enums.TransactionType;

/** Optional search filters for transaction history; any field may be null. Dates are UTC and inclusive. */
public record TransactionFilter(Long accountId, LocalDate from, LocalDate to, TransactionType type,
		TransactionStatus status) {
}
