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

@Entity
@Table(name = "payees")
public class Payee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 60)
	private String nickname;

	@Column(name = "account_number", nullable = false, length = 20)
	private String accountNumber;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Payee() {
	}

	public Payee(User user, String nickname, String accountNumber) {
		this.user = user;
		this.nickname = nickname;
		this.accountNumber = accountNumber;
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

	public String getNickname() {
		return nickname;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
