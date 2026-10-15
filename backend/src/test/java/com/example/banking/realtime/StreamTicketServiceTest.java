package com.example.banking.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class StreamTicketServiceTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-15T10:00:00Z"));

	private final StreamTicketService service = new StreamTicketService(clock);

	@Test
	void aTicketWorksOnceForItsUser() {
		String ticket = service.issue(7L, true);

		assertThat(service.redeem(ticket)).hasValueSatisfying(t -> {
			assertThat(t.userId()).isEqualTo(7L);
			assertThat(t.admin()).isTrue();
		});
		assertThat(service.redeem(ticket)).isEmpty();
	}

	@Test
	void anExpiredTicketIsRejected() {
		String ticket = service.issue(7L, false);
		clock.advance(Duration.ofSeconds(61));

		assertThat(service.redeem(ticket)).isEmpty();
	}

	@Test
	void ticketsAreUnguessableAndUnique() {
		String first = service.issue(1L, false);
		String second = service.issue(1L, false);

		assertThat(first).hasSize(43).isNotEqualTo(second); // 32 random bytes, Base64url
		assertThat(service.redeem(null)).isEmpty();
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
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
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

	}

}
