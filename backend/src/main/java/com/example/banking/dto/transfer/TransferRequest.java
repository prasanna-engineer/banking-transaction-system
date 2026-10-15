package com.example.banking.dto.transfer;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The destination is given EITHER by id (one of the caller's own accounts, as listed by GET /accounts)
 * OR by account number (someone else's account, the way a real payee is entered) - never both.
 * amount may be sent as a JSON string ("1250.00") or number; more than two decimals is rejected, not rounded.
 */
public record TransferRequest(
		@NotNull @Positive Long sourceAccountId,
		@Positive Long destinationAccountId,
		@Pattern(regexp = "\\d{6,20}", message = "must be 6 to 20 digits") String destinationAccountNumber,
		@NotNull @DecimalMin(value = "0.01", message = "must be at least 0.01")
		@Digits(integer = 15, fraction = 2, message = "must have at most 2 decimal places") BigDecimal amount,
		@Size(max = 255) String description) {

	/** Convenience constructor for the common "by id" case. */
	public TransferRequest(Long sourceAccountId, Long destinationAccountId, BigDecimal amount, String description) {
		this(sourceAccountId, destinationAccountId, null, amount, description);
	}

	@JsonIgnore
	@AssertTrue(message = "provide exactly one of destinationAccountId or destinationAccountNumber")
	public boolean isDestination() {
		return (destinationAccountId == null) != (destinationAccountNumber == null);
	}

}
