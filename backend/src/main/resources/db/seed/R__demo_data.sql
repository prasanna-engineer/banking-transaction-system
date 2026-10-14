-- Fictional demo data for local development only. Never real people, accounts or money.
-- All demo users share the password: Demo@12345  (BCrypt hash below, cost 10)
-- Repeatable migration (R__): Flyway re-runs it whenever this file changes, so every
-- statement must be safe to run more than once (INSERT IGNORE skips existing rows).

SET @now = UTC_TIMESTAMP(6);
SET @demo_hash = '$2y$10$EjIAn2syS5xKkjc4hPIoZOhhJFob0xDR2FCmqleisYa/EoiJVxrpm';

INSERT IGNORE INTO users (full_name, email, password_hash, role, status, created_at, updated_at) VALUES
    ('Demo Admin',   'admin@example.com', @demo_hash, 'ADMIN',    'ACTIVE', @now, @now),
    ('Asha Demo',    'asha@example.com',  @demo_hash, 'CUSTOMER', 'ACTIVE', @now, @now),
    ('Ravi Demo',    'ravi@example.com',  @demo_hash, 'CUSTOMER', 'ACTIVE', @now, @now);

INSERT IGNORE INTO bank_accounts (user_id, account_number, account_type, balance, currency, status, created_at, updated_at)
SELECT u.id, a.account_number, a.account_type, a.balance, 'INR', 'ACTIVE', @now, @now
FROM (
    SELECT 'asha@example.com' AS email, '100000000001' AS account_number, 'SAVINGS' AS account_type, 50000.00 AS balance
    UNION ALL SELECT 'asha@example.com', '100000000002', 'CURRENT', 12500.50
    UNION ALL SELECT 'ravi@example.com', '100000000003', 'SAVINGS', 8000.00
) a
JOIN users u ON u.email = a.email;
