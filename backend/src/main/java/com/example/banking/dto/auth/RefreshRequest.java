package com.example.banking.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {

	@Override
	public String toString() {
		return "RefreshRequest[refreshToken=***]";
	}

}
