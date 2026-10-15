package com.example.banking.realtime;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * The browser's EventSource cannot send an Authorization header, so the token cannot go with the
 * stream request. Instead the client first asks (with its token) for a ticket: a random value that is
 * valid for 60 seconds and can be used exactly once to open the stream. Even if it ends up in a log,
 * it is useless almost immediately - unlike the access token itself.
 */
@Service
public class StreamTicketService {

	static final Duration TICKET_LIFETIME = Duration.ofSeconds(60);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

	private final Clock clock;

	public StreamTicketService() {
		this(Clock.systemUTC());
	}

	StreamTicketService(Clock clock) {
		this.clock = clock;
	}

	public String issue(Long userId, boolean admin) {
		Instant now = clock.instant();
		tickets.values().removeIf(ticket -> ticket.expiresAt().isBefore(now));
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		tickets.put(value, new Ticket(userId, admin, now.plus(TICKET_LIFETIME)));
		return value;
	}

	/** Single use: the ticket is removed whether or not it is still valid. */
	public Optional<Ticket> redeem(String value) {
		if (value == null) {
			return Optional.empty();
		}
		Ticket ticket = tickets.remove(value);
		return Optional.ofNullable(ticket).filter(t -> t.expiresAt().isAfter(clock.instant()));
	}

	public record Ticket(Long userId, boolean admin, Instant expiresAt) {
	}

}
