package com.example.banking.entity;

import java.time.Instant;
import java.util.Locale;

import com.example.banking.enums.Role;
import com.example.banking.enums.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Column(nullable = false, unique = true, length = 150)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	// Copied into every access token ("tv" claim). Incrementing it invalidates all tokens issued before.
	@Column(name = "token_version", nullable = false)
	private int tokenVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	// JPA builds objects from database rows through this no-argument constructor.
	protected User() {
	}

	public User(String fullName, String email, String passwordHash, Role role) {
		this.fullName = fullName.trim();
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.role = role;
		this.status = UserStatus.ACTIVE;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public boolean isAdmin() {
		return role == Role.ADMIN;
	}

	public boolean isActive() {
		return status == UserStatus.ACTIVE;
	}

	public void changeStatus(UserStatus newStatus) {
		this.status = newStatus;
	}

	public void invalidateAllTokens() {
		tokenVersion++;
	}

	public int getTokenVersion() {
		return tokenVersion;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public UserStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
