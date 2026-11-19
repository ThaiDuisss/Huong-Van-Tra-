CREATE TABLE taste_taxonomy_versions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    version_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    manifest JSON NOT NULL,
    manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    current_slot TINYINT GENERATED ALWAYS AS (CASE WHEN is_current = 1 THEN 1 ELSE NULL END) STORED,
    published_by BINARY(16) NULL,
    published_at DATETIME(6) NULL,
    created_by BINARY(16) NOT NULL,
    updated_by BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT uq_ttv_version_code UNIQUE (version_code),
    CONSTRAINT uq_ttv_current_slot UNIQUE (current_slot),
    CONSTRAINT ck_ttv_state CHECK (state IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_ttv_manifest_object CHECK (JSON_TYPE(manifest) = 'OBJECT'),
    CONSTRAINT ck_ttv_current CHECK (is_current = 0 OR (is_current = 1 AND state = 'PUBLISHED')),
    CONSTRAINT ck_ttv_publish CHECK (
        (state = 'DRAFT' AND published_by IS NULL AND published_at IS NULL)
        OR (state <> 'DRAFT' AND published_by IS NOT NULL AND published_at IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
