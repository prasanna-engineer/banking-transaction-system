-- Append-only business record. A TRANSFER is one row naming both accounts (see project guide, "Transfer ledger rule").
CREATE TABLE transactions (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    reference              VARCHAR(40)   NOT NULL,
    type                   VARCHAR(20)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    source_account_id      BIGINT        NULL,
    destination_account_id BIGINT        NULL,
    amount                 DECIMAL(19,2) NOT NULL,
    currency               CHAR(3)       NOT NULL,
    description            VARCHAR(255)  NULL,
    created_by             BIGINT        NOT NULL,
    created_at             DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transactions_reference UNIQUE (reference),
    KEY idx_transactions_source_created (source_account_id, created_at),
    KEY idx_transactions_destination_created (destination_account_id, created_at),
    KEY idx_transactions_created_at (created_at),
    CONSTRAINT fk_transactions_source FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_transactions_destination FOREIGN KEY (destination_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_transactions_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (type IN ('DEPOSIT', 'TRANSFER')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    -- A deposit only credits an account; a transfer moves money between two different accounts.
    CONSTRAINT chk_transactions_accounts_match_type CHECK (
        (type = 'DEPOSIT' AND source_account_id IS NULL AND destination_account_id IS NOT NULL)
        OR (type = 'TRANSFER' AND source_account_id IS NOT NULL AND destination_account_id IS NOT NULL
            AND source_account_id <> destination_account_id)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
