package com.example.banking.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/**
	 * Money in and money out per calendar month (UTC) for one user's accounts, in one currency.
	 * A move between two of the user's own accounts counts as neither. Written in SQL because it needs
	 * GROUP BY on a formatted date, which JPQL does not express portably.
	 */
	@Query(nativeQuery = true, value = """
			SELECT DATE_FORMAT(t.created_at, '%Y-%m') AS ym,
			       SUM(CASE WHEN d.user_id = :userId AND (s.user_id IS NULL OR s.user_id <> :userId)
			                THEN t.amount ELSE 0 END) AS moneyIn,
			       SUM(CASE WHEN s.user_id = :userId AND d.user_id <> :userId
			                THEN t.amount ELSE 0 END) AS moneyOut
			FROM transactions t
			LEFT JOIN bank_accounts s ON s.id = t.source_account_id
			JOIN bank_accounts d ON d.id = t.destination_account_id
			WHERE (s.user_id = :userId OR d.user_id = :userId)
			  AND t.status = 'COMPLETED'
			  AND t.currency = :currency
			  AND t.created_at >= :from
			GROUP BY ym
			""")
	List<MonthlyFlowRow> monthlyFlow(@Param("userId") Long userId, @Param("currency") String currency,
			@Param("from") Instant from);

}
