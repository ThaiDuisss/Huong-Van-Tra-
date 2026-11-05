-- Prepared for a separately authorized deployment. Task 04A does not apply this migration.
CREATE TABLE catalog_import_approvals (
    id BINARY(16) NOT NULL PRIMARY KEY,
    plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actions_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approved_by BINARY(16) NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    commercial_decision_ref VARCHAR(500) NOT NULL,
    backup_recovery_ref VARCHAR(500) NOT NULL,
    downstream_verification_ref VARCHAR(500) NOT NULL,
    source_revalidation_ref VARCHAR(500) NOT NULL,
    source_revalidated_at DATETIME(6) NOT NULL,
    INDEX idx_commercial_approval_plan (plan_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalog_import_runs (
    plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    source_system VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actions_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_id BINARY(16) NOT NULL,
    executed_by BINARY(16) NOT NULL,
    before_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    after_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entity_count INT NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_commercial_run_approval FOREIGN KEY (approval_id) REFERENCES catalog_import_approvals(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalog_import_entries (
    source_system VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entity_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_id BINARY(16) NOT NULL,
    target_id BINARY(16) NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_id BINARY(16) NOT NULL,
    executed_by BINARY(16) NOT NULL,
    outbox_event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (source_system, entity_type, source_id),
    UNIQUE KEY uq_commercial_target_identity (entity_type, target_id),
    INDEX idx_commercial_entry_plan (plan_hash),
    CONSTRAINT fk_commercial_entry_approval FOREIGN KEY (approval_id) REFERENCES catalog_import_approvals(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
