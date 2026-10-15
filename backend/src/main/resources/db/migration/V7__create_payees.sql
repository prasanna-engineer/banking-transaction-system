-- A customer's saved recipients ("payees"), so a transfer can pick a name instead of typing an account number.
CREATE TABLE payees (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    nickname       VARCHAR(60) NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payees_user_account UNIQUE (user_id, account_number),
    CONSTRAINT fk_payees_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
