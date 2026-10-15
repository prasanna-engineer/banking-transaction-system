package com.example.banking.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.auth.LoginRequest;
import com.example.banking.dto.auth.LoginResponse;
import com.example.banking.dto.auth.RegisterRequest;
import com.example.banking.dto.user.UserResponse;
import com.example.banking.entity.User;
import com.example.banking.enums.Role;
import com.example.banking.exception.ApiException;
import com.example.banking.realtime.LiveEvents;
import com.example.banking.repository.UserRepository;
import com.example.banking.security.JwtTokenService;
import com.example.banking.security.LoginAttemptService;
import com.example.banking.security.RefreshTokenService;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtTokenService tokenService;

	private final RefreshTokenService refreshTokenService;

	private final LoginAttemptService loginAttempts;

	private final CurrentUserService currentUserService;

	private final AuditService auditService;

	private final ApplicationEventPublisher events;

	// Compared against when the email does not exist, so a wrong email takes as long as a wrong password
	// and response timing cannot reveal which emails are registered.
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
			RefreshTokenService refreshTokenService, LoginAttemptService loginAttempts,
			CurrentUserService currentUserService, AuditService auditService, ApplicationEventPublisher events) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.refreshTokenService = refreshTokenService;
		this.loginAttempts = loginAttempts;
		this.currentUserService = currentUserService;
		this.auditService = auditService;
		this.events = events;
		this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		String email = User.normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw emailTaken();
		}
		User user = new User(request.fullName(), email, passwordEncoder.encode(request.password()), Role.CUSTOMER);
		try {
			// saveAndFlush runs the INSERT now, so a simultaneous duplicate hits the unique index here, not at commit.
			return UserResponse.from(userRepository.saveAndFlush(user));
		}
		catch (DataIntegrityViolationException ex) {
			throw emailTaken();
		}
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		String email = User.normalizeEmail(request.email());
		// Brute-force protection first: a locked email is refused before the password is even checked.
		loginAttempts.checkAllowed(email);

		Optional<User> found = userRepository.findByEmail(email);
		boolean passwordMatches = passwordEncoder.matches(request.password(),
				found.map(user -> user.getPasswordHash()).orElse(dummyPasswordHash));
		// One message for every failure: unknown email, wrong password or disabled account.
		User user = found.filter(u -> passwordMatches && u.isActive()).orElseThrow(() -> {
			loginAttempts.recordFailure(email);
			// Logged without the email or password: enough to spot brute-force patterns, nothing to steal.
			log.warn("Failed login attempt (known user: {})", found.isPresent());
			return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
		});
		loginAttempts.recordSuccess(email);
		log.info("User {} logged in", user.getId());
		return tokensFor(user, refreshTokenService.issue(user));
	}

	/**
	 * Swaps a refresh token for a new access token AND a new refresh token (rotation).
	 * noRollbackFor: when a reused token is detected, revoking its family must be committed even though
	 * the caller gets a 401 - otherwise throwing the error would roll the revocation back.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public LoginResponse refresh(String rawRefreshToken) {
		RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken)
			.filter(r -> r.user().isActive())
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
					"Your session has ended. Please sign in again."));
		return tokensFor(rotation.user(), rotation.newToken());
	}

	/** Signs out this session: its refresh token (and the rest of its family) stops working. */
	@Transactional
	public void logout(String rawRefreshToken) {
		refreshTokenService.revokeFamilyOf(rawRefreshToken);
	}

	/**
	 * Signs out every session of this user: all refresh tokens are revoked, every access token already
	 * issued becomes invalid (token version bump), and open browser tabs are told immediately.
	 */
	@Transactional
	public void logoutEverywhere(Long currentUserId) {
		User user = currentUserService.requireActiveUser(currentUserId);
		user.invalidateAllTokens();
		refreshTokenService.revokeAll(user.getId());
		auditService.record(user, "LOGOUT_ALL", "USER", user.getId(), Map.of("tokenVersion", user.getTokenVersion()));
		events.publishEvent(new LiveEvents.SessionEnded(user.getId()));
		log.info("User {} signed out of all sessions", user.getId());
	}

	private LoginResponse tokensFor(User user, RefreshTokenService.IssuedRefreshToken refresh) {
		JwtTokenService.AccessToken access = tokenService.createAccessToken(user.getId(), user.getRole(),
				user.getTokenVersion());
		Instant now = Instant.now();
		return new LoginResponse(access.value(), "Bearer", Duration.between(now, access.expiresAt()).toSeconds(),
				refresh.value(), Duration.between(now, refresh.expiresAt()).toSeconds(), UserResponse.from(user));
	}

	private static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "This email is already registered.");
	}

}
