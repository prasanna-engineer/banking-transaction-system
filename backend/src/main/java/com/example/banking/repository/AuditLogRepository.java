package com.example.banking.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.banking.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

	List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);

	// Loads each row's actor in the same query, so showing "who did it" costs no extra queries.
	@Override
	@EntityGraph(attributePaths = "actor")
	Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);

}
