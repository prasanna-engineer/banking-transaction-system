package com.example.banking.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.example.banking.repository.UserRepository;
import com.example.banking.security.JwtTokenService;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtTokenService tokenService;

	// Compared against when the email does not exist, so a wrong email takes as long as a wrong password
	// and response timing cannot reveal which emails are registered.
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService tokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
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

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		Optional<User> found = userRepository.findByEmail(User.normalizeEmail(request.email()));
		boolean passwordMatches = passwordEncoder.matches(request.password(),
				found.map(user -> user.getPasswordHash()).orElse(dummyPasswordHash));
		// One message for every failure: unknown email, wrong password or disabled account.
		User user = found.filter(u -> passwordMatches && u.isActive()).orElseThrow(() -> {
			// Logged without the email or password: enough to spot brute-force patterns, nothing to steal.
			log.warn("Failed login attempt (known user: {})", found.isPresent());
			return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
		});
		log.info("User {} logged in", user.getId());

		JwtTokenService.AccessToken token = tokenService.createAccessToken(user.getId(), user.getRole());
		long expiresIn = Duration.between(Instant.now(), token.expiresAt()).toSeconds();
		return new LoginResponse(token.value(), "Bearer", expiresIn, UserResponse.from(user));
	}

	private static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "This email is already registered.");
	}

}
