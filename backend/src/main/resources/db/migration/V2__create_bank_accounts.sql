CREATE TABLE bank_accounts (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL,
    account_number VARCHAR(20)   NOT NULL,
    account_type   VARCHAR(20)   NOT NULL,
    balance        DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    currency       CHAR(3)       NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bank_accounts_account_number UNIQUE (account_number),
    KEY idx_bank_accounts_user_id (user_id),
    CONSTRAINT fk_bank_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_bank_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_bank_accounts_type CHECK (account_type IN ('SAVINGS', 'CURRENT')),
    CONSTRAINT chk_bank_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
