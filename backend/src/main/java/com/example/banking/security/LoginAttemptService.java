package com.example.banking.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.banking.exception.ApiException;

/**
 * Brute-force protection for login: after {@value #MAX_FAILURES} failed attempts for the same email
 * within {@link #WINDOW}, further attempts are refused for {@link #LOCK_DURATION}.
 *
 * Keyed by the (normalised) email whether or not an account exists - otherwise "locked" vs "wrong
 * password" would reveal which emails are registered.
 *
 * Kept in memory: fine for one server. Several servers would share the counters (e.g. in Redis), and a
 * real system would also limit per IP address.
 */
@Service
public class LoginAttemptService {

	static final int MAX_FAILURES = 5;

	static final Duration WINDOW = Duration.ofMinutes(15);

	static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

	private final Clock clock;

	public LoginAttemptService() {
		this(Clock.systemUTC());
	}

	LoginAttemptService(Clock clock) {
		this.clock = clock;
	}

	/** Throws 429 TOO_MANY_LOGIN_ATTEMPTS (with Retry-After) while the email is locked. */
	public void checkAllowed(String email) {
		Attempts current = attempts.get(email);
		Instant now = clock.instant();
		if (current != null && current.lockedUntil() != null && now.isBefore(current.lockedUntil())) {
			long retryAfter = Math.max(1, Duration.between(now, current.lockedUntil()).toSeconds());
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_LOGIN_ATTEMPTS",
					"Too many failed sign-in attempts. Try again in " + Math.ceilDiv(retryAfter, 60) + " minute(s).")
				.withRetryAfter(retryAfter);
		}
	}

	public void recordFailure(String email) {
		Instant now = clock.instant();
		attempts.compute(email, (key, previous) -> {
			// Start counting again when there is no history, the window has passed, or a lock has expired.
			if (previous == null || now.isAfter(previous.windowStart().plus(WINDOW))
					|| (previous.lockedUntil() != null && !now.isBefore(previous.lockedUntil()))) {
				return new Attempts(1, now, null);
			}
			int failures = previous.failures() + 1;
			return new Attempts(failures, previous.windowStart(),
					failures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null);
		});
	}

	public void recordSuccess(String email) {
		attempts.remove(email);
	}

	/** Forget stale entries so the map cannot grow forever. */
	@Scheduled(fixedRate = 600_000)
	public void purgeExpired() {
		Instant now = clock.instant();
		attempts.values()
			.removeIf(a -> now.isAfter(a.windowStart().plus(WINDOW))
					&& (a.lockedUntil() == null || now.isAfter(a.lockedUntil())));
	}

	private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {
	}

}
