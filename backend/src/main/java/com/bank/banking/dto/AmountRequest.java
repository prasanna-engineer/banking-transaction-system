package com.bank.banking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for deposit and withdraw. */
public record AmountRequest(
		@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
		@Size(max = 255) String description) {
}
