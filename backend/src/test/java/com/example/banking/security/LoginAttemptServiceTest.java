package com.example.banking.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.example.banking.exception.ApiException;

class LoginAttemptServiceTest {

	private final MovableClock clock = new MovableClock(Instant.parse("2026-10-15T10:00:00Z"));

	private final LoginAttemptService service = new LoginAttemptService(clock);

	@Test
	void fourFailuresAreAllowedTheFifthLocks() {
		fail(4);
		assertThatCode(() -> service.checkAllowed("a@example.com")).doesNotThrowAnyException();

		fail(1);
		assertThatThrownBy(() -> service.checkAllowed("a@example.com")).isInstanceOfSatisfying(ApiException.class,
				ex -> org.assertj.core.api.Assertions.assertThat(ex.getRetryAfterSeconds()).isEqualTo(900));
	}

	@Test
	void theLockExpiresAfter15Minutes() {
		fail(5);
		clock.advance(Duration.ofMinutes(15).plusSeconds(1));
		assertThatCode(() -> service.checkAllowed("a@example.com")).doesNotThrowAnyException();
	}

	@Test
	void failuresSpreadOverMoreThanTheWindowDoNotLock() {
		fail(4);
		clock.advance(Duration.ofMinutes(16));
		fail(1);
		assertThatCode(() -> service.checkAllowed("a@example.com")).doesNotThrowAnyException();
	}

	@Test
	void aSuccessfulSignInResetsTheCount() {
		fail(4);
		service.recordSuccess("a@example.com");
		fail(4);
		assertThatCode(() -> service.checkAllowed("a@example.com")).doesNotThrowAnyException();
	}

	private void fail(int times) {
		for (int i = 0; i < times; i++) {
			service.recordFailure("a@example.com");
		}
	}

	private static final class MovableClock extends Clock {

		private Instant now;

		MovableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
