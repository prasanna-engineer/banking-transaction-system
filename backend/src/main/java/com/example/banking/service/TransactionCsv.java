package com.example.banking.service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.example.banking.entity.BankAccount;
import com.example.banking.entity.BankTransaction;

/** Turns transactions into an RFC 4180 CSV statement that is safe to open in Excel or Google Sheets. */
final class TransactionCsv {

	// Excel only reads a CSV file as UTF-8 when it starts with this byte-order mark.
	private static final String UTF8_BOM = "﻿";

	private static final String HEADER = "Date (UTC),Reference,Type,Status,Direction,From,To,Amount,Currency,Description";

	private TransactionCsv() {
	}

	/** @param viewerId the customer the statement is for (Debit/Credit from their side); null for an admin. */
	static String write(List<BankTransaction> transactions, Long viewerId) {
		String body = transactions.stream().map(tx -> row(tx, viewerId)).collect(Collectors.joining("\r\n"));
		return UTF8_BOM + HEADER + "\r\n" + body + (body.isEmpty() ? "" : "\r\n");
	}

	private static String row(BankTransaction tx, Long viewerId) {
		return Stream
			.of(tx.getCreatedAt().toString(), tx.getReference(), tx.getType().name(), tx.getStatus().name(),
					direction(tx, viewerId), mask(tx.getSourceAccount()), mask(tx.getDestinationAccount()),
					tx.getAmount().toPlainString(), tx.getCurrency(), tx.getDescription())
			.map(TransactionCsv::cell)
			.collect(Collectors.joining(","));
	}

	private static String direction(BankTransaction tx, Long viewerId) {
		if (viewerId == null) {
			return "";
		}
		boolean fromMe = owns(tx.getSourceAccount(), viewerId);
		boolean toMe = owns(tx.getDestinationAccount(), viewerId);
		return fromMe && toMe ? "Internal" : fromMe ? "Debit" : "Credit";
	}

	private static boolean owns(BankAccount account, Long userId) {
		return account != null && account.getUser().getId().equals(userId);
	}

	private static String mask(BankAccount account) {
		if (account == null) {
			return "Deposit";
		}
		String number = account.getAccountNumber();
		return "****" + number.substring(number.length() - 4);
	}

	/**
	 * Quotes every value (doubling inner quotes) and neutralises "CSV injection": a cell starting with
	 * = + - @ (or tab / carriage return) would be run as a FORMULA by spreadsheet apps, e.g. a transfer
	 * description of =HYPERLINK("http://evil",...). Prefixing a single quote makes it plain text (OWASP).
	 */
	static String cell(String value) {
		if (value == null) {
			return "\"\"";
		}
		String safe = value;
		if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
			safe = "'" + safe;
		}
		return "\"" + safe.replace("\"", "\"\"") + "\"";
	}

}
