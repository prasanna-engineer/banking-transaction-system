-- "Sign out everywhere": every access token carries the user's token_version; bumping it makes all
-- previously issued access tokens invalid at once.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;

-- Long-lived refresh tokens. Only a SHA-256 hash is stored (like a password), each token is single-use
-- (rotated on every refresh), and tokens from one login share a family_id so reuse of an old token can
-- revoke the whole family.
CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    family_id  CHAR(36)    NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    KEY idx_refresh_tokens_family (family_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
