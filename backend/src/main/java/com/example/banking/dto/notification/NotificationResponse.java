package com.example.banking.dto.notification;

import java.time.Instant;

import com.example.banking.entity.Notification;
import com.example.banking.enums.NotificationType;

public record NotificationResponse(Long id, NotificationType type, String title, String message, String reference,
		boolean read, Instant createdAt) {

	public static NotificationResponse from(Notification n) {
		return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getReference(),
				n.getReadAt() != null, n.getCreatedAt());
	}

}
