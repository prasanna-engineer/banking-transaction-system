package com.example.banking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.banking.entity.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

	// Loads the stored transfer and its accounts too, so the original response can be rebuilt in one query.
	@EntityGraph(attributePaths = { "transaction", "transaction.sourceAccount", "transaction.destinationAccount" })
	Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

}
