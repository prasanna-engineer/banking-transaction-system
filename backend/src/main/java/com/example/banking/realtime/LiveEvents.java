package com.example.banking.realtime;

import java.util.Set;

/**
 * Application events that end up as Server-Sent Events in the browser. They are published inside a
 * database transaction and delivered by {@link SseHub} only AFTER that transaction commits, so a browser
 * is never told about something that was rolled back.
 */
public final class LiveEvents {

	private LiveEvents() {
	}

	/** Push {@code data} as event {@code name} to these users' open browser tabs. */
	public record ForUsers(Set<Long> userIds, String name, Object data) {
	}

	/** Push to every connected administrator (e.g. the live audit feed). */
	public record ForAdmins(String name, Object data) {
	}

	/** Tell this user's browser tabs that their session is over (e.g. an admin disabled the account). */
	public record SessionEnded(Long userId) {
	}

}
