package com.bank.banking.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.bank.banking.exception.BusinessException;

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
@Table(name = "accounts")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_number", nullable = false, unique = true, length = 12)
	private String accountNumber;

	@Column(name = "holder_name", nullable = false, length = 100)
	private String holderName;

	@Column(nullable = false, length = 150)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	// Money is always BigDecimal, never double: double cannot represent 0.10 exactly.
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal balance;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	// JPA needs a no-argument constructor to build objects from database rows.
	protected Account() {
	}

	public Account(String accountNumber, String holderName, String email, AccountType type) {
		this.accountNumber = accountNumber;
		this.holderName = holderName;
		this.email = email;
		this.type = type;
		this.status = AccountStatus.ACTIVE;
		this.balance = BigDecimal.ZERO.setScale(2);
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

	public void deposit(BigDecimal amount) {
		ensureActive();
		balance = balance.add(amount);
	}

	public void withdraw(BigDecimal amount) {
		ensureActive();
		if (balance.compareTo(amount) < 0) {
			throw new BusinessException("Insufficient funds in account " + accountNumber);
		}
		balance = balance.subtract(amount);
	}

	public void close() {
		ensureActive();
		if (balance.signum() != 0) {
			throw new BusinessException("Account balance must be zero before closing");
		}
		status = AccountStatus.CLOSED;
	}

	private void ensureActive() {
		if (status != AccountStatus.ACTIVE) {
			throw new BusinessException("Account " + accountNumber + " is closed");
		}
	}

	public Long getId() {
		return id;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public String getHolderName() {
		return holderName;
	}

	public String getEmail() {
		return email;
	}

	public AccountType getType() {
		return type;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
