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

/** A refresh token as stored: only its SHA-256 hash, never the token itself. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(User user, String familyId, String tokenHash, Instant expiresAt) {
		this.user = user;
		this.familyId = familyId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public void revoke() {
		if (revokedAt == null) {
			revokedAt = Instant.now();
		}
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getFamilyId() {
		return familyId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

}
