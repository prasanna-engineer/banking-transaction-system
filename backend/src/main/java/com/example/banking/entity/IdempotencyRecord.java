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

/** Remembers which transfer a user's Idempotency-Key produced, so a retry can return the same result. */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "idempotency_key", nullable = false, length = 100)
	private String idempotencyKey;

	// SHA-256 of the request body: the same key with different data is rejected instead of replayed.
	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	// Filled in once the transfer has been written, still inside the same database transaction.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "transaction_id")
	private BankTransaction transaction;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected IdempotencyRecord() {
	}

	public IdempotencyRecord(User user, String idempotencyKey, String requestHash) {
		this.user = user;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
	}

	public void complete(BankTransaction transaction) {
		this.transaction = transaction;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public BankTransaction getTransaction() {
		return transaction;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
