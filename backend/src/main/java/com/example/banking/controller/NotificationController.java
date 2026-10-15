package com.example.banking.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.banking.dto.PageResponse;
import com.example.banking.dto.notification.NotificationResponse;
import com.example.banking.entity.User;
import com.example.banking.realtime.SseHub;
import com.example.banking.realtime.StreamTicketService;
import com.example.banking.security.CurrentUserId;
import com.example.banking.service.CurrentUserService;
import com.example.banking.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notifications", description = "In-app notifications and the live event stream")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	private final CurrentUserService currentUserService;

	private final StreamTicketService ticketService;

	private final SseHub sseHub;

	public NotificationController(NotificationService notificationService, CurrentUserService currentUserService,
			StreamTicketService ticketService, SseHub sseHub) {
		this.notificationService = notificationService;
		this.currentUserService = currentUserService;
		this.ticketService = ticketService;
		this.sseHub = sseHub;
	}

	@GetMapping
	public PageResponse<NotificationResponse> list(@CurrentUserId Long currentUserId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return notificationService.list(currentUserService.requireActiveUser(currentUserId).getId(), page, size);
	}

	@GetMapping("/unread-count")
	public Map<String, Long> unreadCount(@CurrentUserId Long currentUserId) {
		return Map.of("count", notificationService.unreadCount(currentUserService.requireActiveUser(currentUserId).getId()));
	}

	@PatchMapping("/{id}/read")
	public NotificationResponse markRead(@CurrentUserId Long currentUserId, @PathVariable Long id) {
		return notificationService.markRead(currentUserService.requireActiveUser(currentUserId).getId(), id);
	}

	@PostMapping("/read-all")
	public Map<String, Integer> markAllRead(@CurrentUserId Long currentUserId) {
		return Map.of("updated", notificationService.markAllRead(currentUserService.requireActiveUser(currentUserId).getId()));
	}

	@Operation(summary = "Get a one-time ticket for the live stream",
			description = "Valid for 60 seconds and usable once. EventSource cannot send an Authorization header, so the stream is opened with this ticket instead of the access token.")
	@PostMapping("/stream-ticket")
	public Map<String, Object> streamTicket(@CurrentUserId Long currentUserId) {
		User user = currentUserService.requireActiveUser(currentUserId);
		return Map.of("ticket", ticketService.issue(user.getId(), user.isAdmin()), "expiresInSeconds", 60);
	}

	@Operation(summary = "Live event stream (Server-Sent Events)",
			description = "Events: connected, notification, audit (admins), session-ended.")
	@SecurityRequirements // authenticated by the one-time ticket, not the bearer token
	@GetMapping(path = "/stream", produces = "text/event-stream")
	public ResponseEntity<SseEmitter> stream(@RequestParam(required = false) String ticket) {
		return ticketService.redeem(ticket)
			.map(t -> ResponseEntity.ok(sseHub.connect(t.userId(), t.admin())))
			// No JSON body here: the client asked for an event stream, so an empty 401 is the honest answer.
			.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

}
