package com.example.banking.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Keeps the open Server-Sent Events connections (one per browser tab) and pushes events to them.
 *
 * Scaling note: connections live in this server's memory, so this works for ONE backend instance.
 * With several instances, each event must be broadcast to all of them (e.g. via Redis pub/sub) so the
 * instance holding the user's connection can deliver it.
 */
@Component
public class SseHub {

	private static final Logger log = LoggerFactory.getLogger(SseHub.class);

	// After this the browser reconnects automatically (with a fresh ticket).
	static final Duration CONNECTION_TIMEOUT = Duration.ofMinutes(30);

	private final Map<Long, List<Connection>> connections = new ConcurrentHashMap<>();

	public SseEmitter connect(Long userId, boolean admin) {
		SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT.toMillis());
		Connection connection = new Connection(emitter, admin);
		connections.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(connection);
		Runnable remove = () -> remove(userId, connection);
		emitter.onCompletion(remove);
		emitter.onTimeout(remove);
		emitter.onError(error -> remove.run());
		send(userId, connection, "connected", Map.of("userId", userId));
		return emitter;
	}

	// ---- Delivery happens only after the database transaction has committed. ----

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onUserEvent(LiveEvents.ForUsers event) {
		event.userIds().forEach(userId -> sendToUser(userId, event.name(), event.data()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onAdminEvent(LiveEvents.ForAdmins event) {
		connections.forEach((userId, list) -> list.stream()
			.filter(Connection::admin)
			.forEach(connection -> send(userId, connection, event.name(), event.data())));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onSessionEnded(LiveEvents.SessionEnded event) {
		List<Connection> list = connections.remove(event.userId());
		if (list != null) {
			list.forEach(connection -> {
				send(event.userId(), connection, "session-ended", Map.of("reason", "ACCOUNT_DISABLED"));
				connection.emitter().complete();
			});
		}
	}

	/** Comment lines every 25 s keep proxies from closing idle connections and reveal dead ones. */
	@Scheduled(fixedRate = 25_000)
	public void heartbeat() {
		connections.forEach((userId, list) -> list.forEach(connection -> {
			try {
				connection.emitter().send(SseEmitter.event().comment("keep-alive"));
			}
			catch (IOException | IllegalStateException ex) {
				remove(userId, connection);
			}
		}));
	}

	public int connectionCount(Long userId) {
		List<Connection> list = connections.get(userId);
		return list == null ? 0 : list.size();
	}

	private void sendToUser(Long userId, String name, Object data) {
		List<Connection> list = connections.get(userId);
		if (list != null) {
			list.forEach(connection -> send(userId, connection, name, data));
		}
	}

	private void send(Long userId, Connection connection, String name, Object data) {
		try {
			connection.emitter().send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
		}
		catch (IOException | IllegalStateException ex) {
			// Normal: the tab was closed or the network dropped. Forget this connection.
			log.debug("Dropping closed SSE connection for user {}", userId);
			remove(userId, connection);
		}
		catch (RuntimeException ex) {
			// Unexpected (e.g. the data could not be serialised): make it visible instead of failing silently.
			log.warn("Could not send SSE event '{}' to user {}", name, userId, ex);
			remove(userId, connection);
		}
	}

	private void remove(Long userId, Connection connection) {
		connections.computeIfPresent(userId, (id, list) -> {
			list.remove(connection);
			return list.isEmpty() ? null : list;
		});
	}

	private record Connection(SseEmitter emitter, boolean admin) {
	}

}
