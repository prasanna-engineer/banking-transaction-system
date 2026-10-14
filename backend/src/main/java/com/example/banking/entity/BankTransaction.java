package com.example.banking.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.banking.enums.TransactionStatus;
import com.example.banking.enums.TransactionType;

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
import jakarta.persistence.Table;

/**
 * One money movement. Append-only: there are no setters, so a saved row is never edited.
 * Named BankTransaction to avoid confusion with database transactions (@Transactional).
 */
@Entity
@Table(name = "transactions")
public class BankTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 40)
	private String reference;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransactionType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransactionStatus status;

	// Null for a DEPOSIT (money comes from outside the system).
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_account_id")
	private BankAccount sourceAccount;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "destination_account_id")
	private BankAccount destinationAccount;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(length = 255)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false)
	private User createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected BankTransaction() {
	}

	private BankTransaction(String reference, TransactionType type, BankAccount sourceAccount,
			BankAccount destinationAccount, BigDecimal amount, String description, User createdBy) {
		this.reference = reference;
		this.type = type;
		this.status = TransactionStatus.COMPLETED;
		this.sourceAccount = sourceAccount;
		this.destinationAccount = destinationAccount;
		this.amount = amount;
		this.currency = destinationAccount.getCurrency();
		this.description = description;
		this.createdBy = createdBy;
	}

	public static BankTransaction deposit(String reference, BankAccount destination, BigDecimal amount,
			String description, User createdBy) {
		return new BankTransaction(reference, TransactionType.DEPOSIT, null, destination, amount, description,
				createdBy);
	}

	public static BankTransaction transfer(String reference, BankAccount source, BankAccount destination,
			BigDecimal amount, String description, User createdBy) {
		return new BankTransaction(reference, TransactionType.TRANSFER, source, destination, amount, description,
				createdBy);
	}

	/** True if the given user owns the source or the destination account. */
	public boolean involvesUser(Long userId) {
		return ownedBy(sourceAccount, userId) || ownedBy(destinationAccount, userId);
	}

	private static boolean ownedBy(BankAccount account, Long userId) {
		return account != null && account.getUser().getId().equals(userId);
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

	public TransactionType getType() {
		return type;
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public BankAccount getSourceAccount() {
		return sourceAccount;
	}

	public BankAccount getDestinationAccount() {
		return destinationAccount;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public String getDescription() {
		return description;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
