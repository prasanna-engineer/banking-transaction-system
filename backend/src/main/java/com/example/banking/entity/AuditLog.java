package com.example.banking.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Append-only record of an important action. No setters: audit history is never edited. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Null for actions performed by the system itself rather than a user.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_user_id")
	private User actor;

	@Column(nullable = false, length = 50)
	private String action;

	@Column(name = "entity_type", nullable = false, length = 50)
	private String entityType;

	@Column(name = "entity_id")
	private Long entityId;

	@Column(name = "metadata_json", length = 2000)
	private String metadataJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditLog() {
	}

	public AuditLog(User actor, String action, String entityType, Long entityId, String metadataJson) {
		this.actor = actor;
		this.action = action;
		this.entityType = entityType;
		this.entityId = entityId;
		this.metadataJson = metadataJson;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public User getActor() {
		return actor;
	}

	public String getAction() {
		return action;
	}

	public String getEntityType() {
		return entityType;
	}

	public Long getEntityId() {
		return entityId;
	}

	public String getMetadataJson() {
		return metadataJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
