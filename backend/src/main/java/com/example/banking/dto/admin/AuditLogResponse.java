package com.example.banking.dto.admin;

import java.time.Instant;

import com.example.banking.entity.AuditLog;
import com.example.banking.entity.User;

/** metadataJson is passed through as the JSON text that was stored (it never contains secrets). */
public record AuditLogResponse(Long id, Long actorUserId, String actorEmail, String action, String entityType,
		Long entityId, String metadataJson, Instant createdAt) {

	public static AuditLogResponse from(AuditLog log) {
		User actor = log.getActor();
		return new AuditLogResponse(log.getId(), actor == null ? null : actor.getId(),
				actor == null ? null : actor.getEmail(), log.getAction(), log.getEntityType(), log.getEntityId(),
				log.getMetadataJson(), log.getCreatedAt());
	}

}
