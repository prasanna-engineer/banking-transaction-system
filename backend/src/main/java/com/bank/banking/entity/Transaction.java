package com.bank.banking.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One ledger line for one account. A transfer creates two rows (TRANSFER_OUT and
 * TRANSFER_IN) that share the same reference.
 */
@Entity
@Table(name = "transactions", indexes = {
		@Index(name = "idx_transactions_account_created", columnList = "account_id, created_at"),
		@Index(name = "idx_transactions_reference", columnList = "reference") })
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 36)
	private String reference;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransactionType type;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
	private BigDecimal balanceAfter;

	@Column(name = "counterparty_account_number", length = 12)
	private String counterpartyAccountNumber;

	@Column(length = 255)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Transaction() {
	}

	public Transaction(String reference, Account account, TransactionType type, BigDecimal amount,
			String counterpartyAccountNumber, String description) {
		this.reference = reference;
		this.account = account;
		this.type = type;
		this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
		this.balanceAfter = account.getBalance();
		this.counterpartyAccountNumber = counterpartyAccountNumber;
		this.description = description;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getReference() {
		return reference;
	}

	public Account getAccount() {
		return account;
	}

	public TransactionType getType() {
		return type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public String getCounterpartyAccountNumber() {
		return counterpartyAccountNumber;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
