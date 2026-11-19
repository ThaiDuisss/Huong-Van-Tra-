CREATE TABLE customer_taste_surveys (
    id BINARY(16) NOT NULL PRIMARY KEY,
    customer_id BINARY(16) NULL,
    session_id BINARY(16) NOT NULL,
    owner_actor_id BINARY(16) NOT NULL,
    origin_invoice_id BINARY(16) NOT NULL,
    copied_from_survey_id BINARY(16) NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    raw_description TEXT NULL,
    criteria_json JSON NOT NULL,
    taxonomy_version VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    taxonomy_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    criteria_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    binding_version INT UNSIGNED NOT NULL DEFAULT 0,
    create_request_key BINARY(16) NOT NULL,
    create_request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    submit_request_key BINARY(16) NULL,
    submit_request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_by BINARY(16) NOT NULL,
    updated_by BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    linked_at DATETIME(6) NULL,
    linked_by BINARY(16) NULL,
    CONSTRAINT fk_cts_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cts_copied_from FOREIGN KEY (copied_from_survey_id)
        REFERENCES customer_taste_surveys (id) ON DELETE RESTRICT,
    CONSTRAINT uq_cts_session UNIQUE (session_id),
    CONSTRAINT uq_cts_actor_create UNIQUE (owner_actor_id, create_request_key),
    CONSTRAINT ck_cts_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'EXPIRED')),
    CONSTRAINT ck_cts_criteria_object CHECK (JSON_TYPE(criteria_json) = 'OBJECT'),
    CONSTRAINT ck_cts_submit CHECK (
        (status = 'SUBMITTED' AND submitted_at IS NOT NULL AND criteria_sha256 IS NOT NULL
            AND submit_request_key IS NOT NULL AND submit_request_hash IS NOT NULL)
        OR (status <> 'SUBMITTED' AND submitted_at IS NULL
            AND submit_request_key IS NULL AND submit_request_hash IS NULL)
    ),
    CONSTRAINT ck_cts_guest_expiry CHECK (customer_id IS NOT NULL OR expires_at IS NOT NULL),
    CONSTRAINT ck_cts_binding CHECK (
        (binding_version = 0 AND linked_at IS NULL AND linked_by IS NULL)
        OR (binding_version = 1 AND customer_id IS NOT NULL AND linked_at IS NOT NULL AND linked_by IS NOT NULL)
    ),
    INDEX idx_cts_customer_latest (customer_id, status, submitted_at DESC, id DESC),
    INDEX idx_cts_expiry (status, expires_at),
    INDEX idx_cts_copied_from (copied_from_survey_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
