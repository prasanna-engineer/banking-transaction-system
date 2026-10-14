package com.example.banking.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.banking.entity.BankTransaction;

public interface BankTransactionRepository
		extends JpaRepository<BankTransaction, Long>, JpaSpecificationExecutor<BankTransaction> {

	// @EntityGraph loads both accounts in the same query, avoiding one extra query per row (the "N+1" problem).
	@Override
	@EntityGraph(attributePaths = { "sourceAccount", "destinationAccount" })
	Page<BankTransaction> findAll(Specification<BankTransaction> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = { "sourceAccount", "destinationAccount" })
	Optional<BankTransaction> findById(Long id);

}
