package com.example.banking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.PageResponse;
import com.example.banking.dto.notification.NotificationResponse;
import com.example.banking.entity.BankAccount;
import com.example.banking.entity.BankTransaction;
import com.example.banking.entity.Notification;
import com.example.banking.entity.User;
import com.example.banking.enums.NotificationType;
import com.example.banking.exception.ApiException;
import com.example.banking.realtime.LiveEvents;
import com.example.banking.repository.NotificationRepository;
import com.example.banking.repository.UserRepository;

@Service
public class NotificationService {

	static final String EVENT_NAME = "notification";

	private final NotificationRepository notificationRepository;

	private final UserRepository userRepository;

	private final ApplicationEventPublisher events;

	public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
			ApplicationEventPublisher events) {
		this.notificationRepository = notificationRepository;
		this.userRepository = userRepository;
		this.events = events;
	}

	/**
	 * Called inside the transfer's database transaction (MANDATORY), so the notifications commit or roll
	 * back together with the transfer. The live push to the browser happens only after commit (SseHub).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordTransfer(BankTransaction tx) {
		BankAccount source = tx.getSourceAccount();
		BankAccount destination = tx.getDestinationAccount();
		Long senderId = source.getUser().getId();
		Long receiverId = destination.getUser().getId();
		String amount = money(tx.getAmount(), tx.getCurrency());

		if (senderId.equals(receiverId)) {
			notify(senderId, NotificationType.INTERNAL_TRANSFER, "Moved between your accounts",
					amount + " moved from " + mask(source) + " to " + mask(destination) + ".", tx.getReference());
			return;
		}
		notify(senderId, NotificationType.MONEY_SENT, "Money sent",
				amount + " sent from " + mask(source) + " to " + mask(destination) + ".", tx.getReference());
		notify(receiverId, NotificationType.MONEY_RECEIVED, "Money received",
				amount + " received in " + mask(destination) + " from " + mask(source) + ".", tx.getReference());
	}

	/** For events that are not tied to a transfer, e.g. a scheduled transfer that could not run. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void notify(Long userId, NotificationType type, String title, String message, String reference) {
		User user = userRepository.getReferenceById(userId);
		Notification saved = notificationRepository.save(new Notification(user, type, title, message, reference));
		events.publishEvent(new LiveEvents.ForUsers(Set.of(userId), EVENT_NAME, NotificationResponse.from(saved)));
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
		return PageResponse.from(
				notificationRepository.findByUserId(userId, Paging.of(page, size, Sort.Direction.DESC, "createdAt")),
				NotificationResponse::from);
	}

	@Transactional(readOnly = true)
	public long unreadCount(Long userId) {
		return notificationRepository.countByUserIdAndReadAtIsNull(userId);
	}

	@Transactional
	public NotificationResponse markRead(Long userId, Long notificationId) {
		Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
			.orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found."));
		notification.markRead();
		return NotificationResponse.from(notification);
	}

	@Transactional
	public int markAllRead(Long userId) {
		return notificationRepository.markAllRead(userId, Instant.now());
	}

	private static String money(BigDecimal amount, String currency) {
		return ("INR".equals(currency) ? "₹" : currency + " ") + amount.toPlainString();
	}

	private static String mask(BankAccount account) {
		String number = account.getAccountNumber();
		return "••••" + number.substring(number.length() - 4);
	}

}
