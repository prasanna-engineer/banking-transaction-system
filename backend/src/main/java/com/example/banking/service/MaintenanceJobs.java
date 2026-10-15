package com.example.banking.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.banking.repository.IdempotencyRecordRepository;
import com.example.banking.repository.RefreshTokenRepository;

/** Hourly housekeeping (like a Laravel scheduled command). */
@Component
public class MaintenanceJobs {

	private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

	/** A retry with the same Idempotency-Key is recognised for at least this long. */
	static final Duration IDEMPOTENCY_KEY_RETENTION = Duration.ofHours(24);

	private final IdempotencyRecordRepository idempotencyRepository;

	private final RefreshTokenRepository refreshTokenRepository;

	public MaintenanceJobs(IdempotencyRecordRepository idempotencyRepository,
			RefreshTokenRepository refreshTokenRepository) {
		this.idempotencyRepository = idempotencyRepository;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Scheduled(cron = "0 0 * * * *") // at the start of every hour
	@Transactional
	public void cleanUp() {
		Instant now = Instant.now();
		int keys = idempotencyRepository.deleteCreatedBefore(now.minus(IDEMPOTENCY_KEY_RETENTION));
		// Keep expired refresh tokens one extra day so reuse of a just-expired token is still detected.
		int tokens = refreshTokenRepository.deleteExpiredBefore(now.minus(Duration.ofDays(1)));
		if (keys + tokens > 0) {
			log.info("Housekeeping removed {} idempotency keys and {} refresh tokens", keys, tokens);
		}
	}

}
