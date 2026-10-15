package com.example.banking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.banking.exception.ApiException;

/**
 * One-time codes that confirm a large transfer (step-up authentication).
 *
 * A challenge is bound to ONE user and ONE exact transfer request (by hash), expires after 5 minutes,
 * allows 3 wrong attempts and works once. DEMO: a real bank would send the code by SMS or app push;
 * here the API returns it so the flow can be tried without a phone. Kept in memory (one server).
 */
@Service
public class OtpService {

	static final Duration LIFETIME = Duration.ofMinutes(5);

	static final int MAX_ATTEMPTS = 3;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

	private final Clock clock;

	public OtpService() {
		this(Clock.systemUTC());
	}

	OtpService(Clock clock) {
		this.clock = clock;
	}

	public IssuedOtp issue(Long userId, String requestHash) {
		Instant now = clock.instant();
		challenges.values().removeIf(c -> now.isAfter(c.expiresAt()));
		String id = UUID.randomUUID().toString();
		String code = String.format("%06d", RANDOM.nextInt(1_000_000));
		challenges.put(id, new Challenge(userId, requestHash, code, now.plus(LIFETIME), 0));
		return new IssuedOtp(id, code, LIFETIME.toSeconds());
	}

	/** Succeeds once for the right user, request and code; throws a clear 4xx otherwise. */
	public void verify(Long userId, String requestHash, String challengeId, String code) {
		Challenge challenge = challengeId == null ? null : challenges.get(challengeId);
		if (challenge == null || !challenge.userId().equals(userId) || clock.instant().isAfter(challenge.expiresAt())) {
			throw invalid("This code has expired or is not valid any more. Request a new one.");
		}
		if (!challenge.requestHash().equals(requestHash)) {
			// The code was issued for a different amount, account or description.
			throw invalid("This code was issued for a different transfer. Request a new one.");
		}
		if (code == null || !constantTimeEquals(challenge.code(), code.trim())) {
			int attempts = challenge.attempts() + 1;
			if (attempts >= MAX_ATTEMPTS) {
				challenges.remove(challengeId);
				throw invalid("Too many wrong codes. Request a new one.");
			}
			challenges.put(challengeId, challenge.withAttempts(attempts));
			throw invalid("Wrong code. " + (MAX_ATTEMPTS - attempts) + " attempt(s) left.");
		}
		challenges.remove(challengeId); // single use
	}

	// Compares in time independent of where the strings differ, so timing does not leak digits.
	private static boolean constantTimeEquals(String expected, String actual) {
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
	}

	private static ApiException invalid(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID", message);
	}

	public record IssuedOtp(String challengeId, String demoCode, long expiresInSeconds) {
	}

	private record Challenge(Long userId, String requestHash, String code, Instant expiresAt, int attempts) {

		Challenge withAttempts(int newAttempts) {
			return new Challenge(userId, requestHash, code, expiresAt, newAttempts);
		}

	}

}
