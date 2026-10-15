package com.example.banking.dto.transfer;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * amount may be sent as a JSON string ("1250.00") or number; it is parsed straight into BigDecimal.
 * More than two decimal places is rejected, never silently rounded.
 */
public record TransferRequest(
		@NotNull @Positive Long sourceAccountId,
		@NotNull @Positive Long destinationAccountId,
		@NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01")
		@Digits(integer = 15, fraction = 2, message = "must have at most 2 decimal places") BigDecimal amount,
		@Size(max = 255) String description) {
}
