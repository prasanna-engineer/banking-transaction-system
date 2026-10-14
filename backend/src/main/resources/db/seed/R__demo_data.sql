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

-- History that explains the balances above:
--   100000000001: 52000.00 deposit - 2000.00 transfer out = 50000.00
--   100000000002: 12500.50 deposit                         = 12500.50
--   100000000003:  6000.00 deposit + 2000.00 transfer in  =  8000.00
INSERT IGNORE INTO transactions (reference, type, status, source_account_id, destination_account_id, amount, currency, description, created_by, created_at)
SELECT t.reference, t.type, 'COMPLETED', src.id, dst.id, t.amount, 'INR', t.description, u.id, @now - INTERVAL t.days_ago DAY
FROM (
    SELECT 'TRX-DEMO-0001' AS reference, 'DEPOSIT' AS type, NULL AS source_number, '100000000001' AS destination_number,
           52000.00 AS amount, 'Opening deposit' AS description, 'admin@example.com' AS created_by_email, 30 AS days_ago
    UNION ALL SELECT 'TRX-DEMO-0002', 'DEPOSIT', NULL, '100000000002', 12500.50, 'Opening deposit', 'admin@example.com', 30
    UNION ALL SELECT 'TRX-DEMO-0003', 'DEPOSIT', NULL, '100000000003', 6000.00, 'Opening deposit', 'admin@example.com', 30
    UNION ALL SELECT 'TRX-DEMO-0004', 'TRANSFER', '100000000001', '100000000003', 2000.00, 'Rent share', 'asha@example.com', 5
) t
JOIN users u ON u.email = t.created_by_email
LEFT JOIN bank_accounts src ON src.account_number = t.source_number
JOIN bank_accounts dst ON dst.account_number = t.destination_number;
