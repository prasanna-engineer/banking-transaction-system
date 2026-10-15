package com.example.banking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.banking.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);

}
