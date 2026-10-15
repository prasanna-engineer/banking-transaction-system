package com.example.banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.banking.dto.auth.LoginRequest;
import com.example.banking.dto.auth.LoginResponse;
import com.example.banking.dto.auth.RefreshRequest;
import com.example.banking.dto.auth.RegisterRequest;
import com.example.banking.dto.user.UserResponse;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "Authentication", description = "Register, sign in, renew and end sessions")
@SecurityRequirements // public by default: no token needed (logout-all overrides this)
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@Operation(summary = "Sign in", description = "Returns a 15-minute access token and a single-use refresh token. "
			+ "After 5 failed attempts for an email, sign-in is refused for 15 minutes (429 with Retry-After).")
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@Operation(summary = "Renew tokens", description = "Exchanges a refresh token for new access and refresh tokens. "
			+ "Each refresh token works once; presenting a used one revokes the whole session.")
	@PostMapping("/refresh")
	public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@Operation(summary = "Sign out of this session")
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody RefreshRequest request) {
		authService.logout(request.refreshToken());
	}

	@Operation(summary = "Sign out of all sessions", description = "Invalidates every access and refresh token of "
			+ "the caller and closes their open live connections.", security = @SecurityRequirement(name = "bearerAuth"))
	@PostMapping("/logout-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logoutAll(@CurrentUserId Long currentUserId) {
		authService.logoutEverywhere(currentUserId);
	}

}
