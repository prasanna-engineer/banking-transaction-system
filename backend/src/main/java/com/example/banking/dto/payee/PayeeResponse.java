package com.example.banking.dto.payee;

import java.time.Instant;

import com.example.banking.entity.Payee;

/** The owner typed this account number themselves, so it is returned to them in full (plus a masked form). */
public record PayeeResponse(Long id, String nickname, String accountNumber, String maskedAccountNumber,
		Instant createdAt) {

	public static PayeeResponse from(Payee payee) {
		String number = payee.getAccountNumber();
		return new PayeeResponse(payee.getId(), payee.getNickname(), number,
				"••••" + number.substring(number.length() - 4), payee.getCreatedAt());
	}

}
