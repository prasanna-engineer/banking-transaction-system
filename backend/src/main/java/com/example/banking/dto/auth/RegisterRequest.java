package com.example.banking.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** There is deliberately no "role" field: every self-registered user is a CUSTOMER. */
public record RegisterRequest(
		@NotBlank @Size(max = 100) String fullName,
		@NotBlank @Email @Size(max = 150) String email,
		// BCrypt only uses the first 72 bytes of a password, so longer input is rejected rather than silently cut.
		@NotBlank @Size(min = 8, max = 72) String password) {

	@Override
	public String toString() {
		return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=***]";
	}

}
