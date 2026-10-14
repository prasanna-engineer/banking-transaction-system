package com.example.banking.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank @Size(max = 150) String email,
		@NotBlank @Size(max = 72) String password) {

	// Records print every field in toString(); override so a password can never end up in a log line.
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=***]";
	}

}
