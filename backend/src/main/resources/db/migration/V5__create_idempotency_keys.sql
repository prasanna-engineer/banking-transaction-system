-- One row per (user, Idempotency-Key) for transfers. Inserted in the same database transaction as the
-- transfer, so a row exists only for a transfer that really committed. The unique key is what makes two
-- simultaneous requests with the same key impossible to both succeed.
CREATE TABLE idempotency_keys (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    transaction_id  BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_keys_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_idempotency_keys_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_idempotency_keys_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
