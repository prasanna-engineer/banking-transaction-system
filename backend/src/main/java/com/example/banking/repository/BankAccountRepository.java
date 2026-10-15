package com.example.banking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.banking.entity.BankAccount;

import jakarta.persistence.LockModeType;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

	List<BankAccount> findByUserIdOrderByIdAsc(Long userId);

	// Ownership check built into the query: another customer's account id simply returns empty.
	Optional<BankAccount> findByIdAndUserId(Long id, Long userId);

	Optional<BankAccount> findByAccountNumber(String accountNumber);

	// Returns only the id (not the entity), for the same stale-cache reason as existsByIdAndUserId below.
	@Query("select a.id from BankAccount a where a.accountNumber = :accountNumber")
	Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

	// Checks ownership without loading the entity. Loading it here would put an unlocked copy in
	// Hibernate's cache, and the later FOR UPDATE query would hand back that stale copy.
	boolean existsByIdAndUserId(Long id, Long userId);

	/**
	 * SELECT ... FOR UPDATE: locks the row until the current transaction ends. Any other transaction
	 * that wants to lock the same row waits, so two transfers can never both read the same old balance.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from BankAccount a where a.id = :id")
	Optional<BankAccount> findByIdForUpdate(@Param("id") Long id);

}
