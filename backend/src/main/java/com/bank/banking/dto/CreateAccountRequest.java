package com.bank.banking.dto;

import java.math.BigDecimal;

import com.bank.banking.entity.AccountType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
		@NotBlank @Size(max = 100) String holderName,
		@NotBlank @Email @Size(max = 150) String email,
		@NotNull AccountType accountType,
		@DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal initialDeposit) {
}
