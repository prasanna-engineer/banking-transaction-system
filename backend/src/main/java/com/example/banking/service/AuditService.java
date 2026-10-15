package com.example.banking.service;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.dto.admin.AuditLogResponse;
import com.example.banking.entity.AuditLog;
import com.example.banking.entity.User;
import com.example.banking.realtime.LiveEvents;
import com.example.banking.repository.AuditLogRepository;

import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {

	private final AuditLogRepository auditLogRepository;

	private final JsonMapper jsonMapper;

	private final ApplicationEventPublisher events;

	public AuditService(AuditLogRepository auditLogRepository, JsonMapper jsonMapper,
			ApplicationEventPublisher events) {
		this.auditLogRepository = auditLogRepository;
		this.jsonMapper = jsonMapper;
		this.events = events;
	}

	/**
	 * MANDATORY: must be called inside the caller's database transaction, so the audit row is
	 * committed together with the change it describes, or rolled back together with it.
	 * Metadata must never contain passwords, tokens or other secrets.
	 * Connected administrators see the new entry live, after the transaction commits.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(User actor, String action, String entityType, Long entityId, Map<String, Object> metadata) {
		String json = metadata == null ? null : jsonMapper.writeValueAsString(metadata);
		AuditLog saved = auditLogRepository.save(new AuditLog(actor, action, entityType, entityId, json));
		events.publishEvent(new LiveEvents.ForAdmins("audit", AuditLogResponse.from(saved)));
	}

}
