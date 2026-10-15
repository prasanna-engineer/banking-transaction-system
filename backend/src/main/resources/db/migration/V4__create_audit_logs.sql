-- Who did what, to which record, and when. Never store passwords, tokens or other secrets in metadata_json.
CREATE TABLE audit_logs (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT        NULL,
    action        VARCHAR(50)   NOT NULL,
    entity_type   VARCHAR(50)   NOT NULL,
    entity_id     BIGINT        NULL,
    metadata_json VARCHAR(2000) NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_logs_entity (entity_type, entity_id),
    KEY idx_audit_logs_actor_created (actor_user_id, created_at),
    KEY idx_audit_logs_created_at (created_at),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_audit_logs_metadata_json CHECK (metadata_json IS NULL OR JSON_VALID(metadata_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
