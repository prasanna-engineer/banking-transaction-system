-- Runs on every startup (spring.sql.init.mode=always); IF NOT EXISTS makes it safe to re-run.

CREATE TABLE IF NOT EXISTS accounts (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(12)   NOT NULL,
    holder_name    VARCHAR(100)  NOT NULL,
    email          VARCHAR(150)  NOT NULL,
    type           VARCHAR(20)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    balance        DECIMAL(19,2) NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_accounts_account_number UNIQUE (account_number),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transactions (
    id                          BIGINT        NOT NULL AUTO_INCREMENT,
    reference                   VARCHAR(36)   NOT NULL,
    account_id                  BIGINT        NOT NULL,
    type                        VARCHAR(20)   NOT NULL,
    amount                      DECIMAL(19,2) NOT NULL,
    balance_after               DECIMAL(19,2) NOT NULL,
    counterparty_account_number VARCHAR(12)   NULL,
    description                 VARCHAR(255)  NULL,
    created_at                  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_transactions_account_created (account_id, created_at),
    KEY idx_transactions_reference (reference),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
