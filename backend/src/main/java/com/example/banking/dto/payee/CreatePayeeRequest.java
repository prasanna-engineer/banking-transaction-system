package com.example.banking.dto.payee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePayeeRequest(
		@NotBlank @Size(max = 60) String nickname,
		@NotBlank @Pattern(regexp = "\\d{6,20}", message = "must be 6 to 20 digits") String accountNumber) {
}
