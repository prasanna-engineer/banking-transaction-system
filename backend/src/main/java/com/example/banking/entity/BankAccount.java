package com.example.banking.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.banking.enums.AccountStatus;
import com.example.banking.enums.AccountType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// LAZY: the owning user is only loaded from the database if the code actually uses it.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "account_number", nullable = false, unique = true, length = 20)
	private String accountNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_type", nullable = false, length = 20)
	private AccountType accountType;

	// Money is always BigDecimal, never double: double cannot represent 0.10 exactly.
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal balance;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	// Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE and bumps it,
	// so an update based on stale data fails instead of silently overwriting a newer balance.
	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected BankAccount() {
	}

	public BankAccount(User user, String accountNumber, AccountType accountType, String currency) {
		this.user = user;
		this.accountNumber = accountNumber;
		this.accountType = accountType;
		this.currency = currency;
		this.balance = new BigDecimal("0.00");
		this.status = AccountStatus.ACTIVE;
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

	public User getUser() {
		return user;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public AccountType getAccountType() {
		return accountType;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public String getCurrency() {
		return currency;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
