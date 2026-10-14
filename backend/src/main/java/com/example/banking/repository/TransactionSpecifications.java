package com.example.banking.repository;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

import com.example.banking.entity.BankAccount;
import com.example.banking.entity.BankTransaction;
import com.example.banking.enums.TransactionStatus;
import com.example.banking.enums.TransactionType;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

/** Reusable WHERE-clause pieces for transaction searches, combined with and(). */
public final class TransactionSpecifications {

	private TransactionSpecifications() {
	}

	public static Specification<BankTransaction> involvesUser(Long userId) {
		return (root, query, cb) -> {
			// LEFT joins: a deposit has no source account, and must still match on its destination.
			Join<BankTransaction, BankAccount> source = root.join("sourceAccount", JoinType.LEFT);
			Join<BankTransaction, BankAccount> destination = root.join("destinationAccount", JoinType.LEFT);
			return cb.or(cb.equal(source.get("user").get("id"), userId),
					cb.equal(destination.get("user").get("id"), userId));
		};
	}

	public static Specification<BankTransaction> involvesAccount(Long accountId) {
		return (root, query, cb) -> cb.or(cb.equal(root.get("sourceAccount").get("id"), accountId),
				cb.equal(root.get("destinationAccount").get("id"), accountId));
	}

	public static Specification<BankTransaction> hasType(TransactionType type) {
		return (root, query, cb) -> cb.equal(root.get("type"), type);
	}

	public static Specification<BankTransaction> hasStatus(TransactionStatus status) {
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	public static Specification<BankTransaction> createdAtOrAfter(Instant from) {
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
	}

	public static Specification<BankTransaction> createdBefore(Instant to) {
		return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
	}

}
