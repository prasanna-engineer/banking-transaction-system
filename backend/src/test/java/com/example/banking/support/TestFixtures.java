package com.example.banking.support;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.banking.entity.BankAccount;
import com.example.banking.entity.BankTransaction;
import com.example.banking.entity.User;
import com.example.banking.enums.AccountType;
import com.example.banking.enums.Role;

import jakarta.persistence.EntityManager;

/** Small helpers to create test rows (like Laravel model factories). */
public class TestFixtures {

	private final EntityManager entityManager;

	public TestFixtures(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public User user(String name, Role role) {
		return persist(new User(name, name.toLowerCase().replace(' ', '.') + "@example.com", "hash", role));
	}

	public BankAccount account(User owner, String accountNumber) {
		return persist(new BankAccount(owner, accountNumber, AccountType.SAVINGS, "INR"));
	}

	public BankAccount fundedAccount(User owner, String accountNumber, String balance) {
		return fundedAccount(owner, accountNumber, balance, "INR");
	}

	public BankAccount fundedAccount(User owner, String accountNumber, String balance, String currency) {
		BankAccount account = new BankAccount(owner, accountNumber, AccountType.SAVINGS, currency);
		BigDecimal opening = new BigDecimal(balance);
		if (opening.signum() > 0) {
			account.credit(opening);
		}
		return persist(account);
	}

	public BankTransaction deposit(BankAccount destination, String amount, User createdBy) {
		return persist(BankTransaction.deposit(reference(), destination, new BigDecimal(amount), "Test deposit",
				createdBy));
	}

	public BankTransaction transfer(BankAccount source, BankAccount destination, String amount, User createdBy) {
		return persist(BankTransaction.transfer(reference(), source, destination, new BigDecimal(amount),
				"Test transfer", createdBy));
	}

	/** Writes pending inserts to the database and empties the cache so reads hit MariaDB. */
	public void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private <T> T persist(T entity) {
		entityManager.persist(entity);
		return entity;
	}

	private static String reference() {
		return "TRX-TEST-" + UUID.randomUUID().toString().substring(0, 12);
	}

}
