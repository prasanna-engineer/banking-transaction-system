package com.example.banking.dto.auth;

import com.example.banking.dto.user.UserResponse;

/**
 * expiresIn / refreshExpiresIn are in seconds (OAuth 2.0 token response convention).
 * The refresh token is returned once and never stored by the server in readable form.
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
		long refreshExpiresIn, UserResponse user) {

	@Override
	public String toString() {
		return "LoginResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", user=" + user + ", tokens=***]";
	}

}
