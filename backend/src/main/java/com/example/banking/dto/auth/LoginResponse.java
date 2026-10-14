package com.example.banking.dto.auth;

import com.example.banking.dto.user.UserResponse;

/** expiresIn is in seconds, as in the OAuth 2.0 token response convention. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
