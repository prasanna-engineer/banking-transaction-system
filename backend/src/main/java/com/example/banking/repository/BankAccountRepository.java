package com.example.banking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.banking.entity.BankAccount;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

	List<BankAccount> findByUserIdOrderByIdAsc(Long userId);

	// Ownership check built into the query: another customer's account id simply returns empty.
	Optional<BankAccount> findByIdAndUserId(Long id, Long userId);

	Optional<BankAccount> findByAccountNumber(String accountNumber);

}
