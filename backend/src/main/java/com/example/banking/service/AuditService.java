package com.example.banking.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.entity.AuditLog;
import com.example.banking.entity.User;
import com.example.banking.repository.AuditLogRepository;

import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {

	private final AuditLogRepository auditLogRepository;

	private final JsonMapper jsonMapper;

	public AuditService(AuditLogRepository auditLogRepository, JsonMapper jsonMapper) {
		this.auditLogRepository = auditLogRepository;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * MANDATORY: must be called inside the caller's database transaction, so the audit row is
	 * committed together with the change it describes, or rolled back together with it.
	 * Metadata must never contain passwords, tokens or other secrets.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(User actor, String action, String entityType, Long entityId, Map<String, Object> metadata) {
		String json = metadata == null ? null : jsonMapper.writeValueAsString(metadata);
		auditLogRepository.save(new AuditLog(actor, action, entityType, entityId, json));
	}

}
