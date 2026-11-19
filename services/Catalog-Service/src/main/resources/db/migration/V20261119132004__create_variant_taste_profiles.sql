CREATE TABLE variant_taste_profiles (
    id BINARY(16) NOT NULL PRIMARY KEY,
    variant_id BINARY(16) NOT NULL,
    taxonomy_version_id BINARY(16) NOT NULL,
    profile_revision INT UNSIGNED NOT NULL,
    composition_revision BIGINT UNSIGNED NOT NULL,
    composition_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    basis_snapshot JSON NOT NULL,
    preparation_code VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    preparation_protocol JSON NOT NULL,
    preparation_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    values_json JSON NOT NULL,
    source_method VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    assessed_by BINARY(16) NULL,
    assessed_at DATETIME(6) NULL,
    approved_by BINARY(16) NULL,
    approved_at DATETIME(6) NULL,
    profile_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    approved_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'APPROVED' THEN 1 ELSE NULL END) STORED,
    created_by BINARY(16) NOT NULL,
    updated_by BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    row_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT fk_vtp_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_vtp_taxonomy FOREIGN KEY (taxonomy_version_id)
        REFERENCES taste_taxonomy_versions (id) ON DELETE RESTRICT,
    CONSTRAINT uq_vtp_revision UNIQUE (variant_id, profile_revision),
    CONSTRAINT uq_vtp_approved_slot UNIQUE (variant_id, taxonomy_version_id, preparation_code, approved_slot),
    CONSTRAINT ck_vtp_status CHECK (status IN ('DRAFT', 'APPROVED', 'STALE', 'RETIRED')),
    CONSTRAINT ck_vtp_revision CHECK (profile_revision > 0 AND composition_revision > 0),
    CONSTRAINT ck_vtp_documents CHECK (
        JSON_TYPE(basis_snapshot) = 'OBJECT' AND JSON_TYPE(preparation_protocol) = 'OBJECT'
            AND JSON_TYPE(values_json) = 'OBJECT'
    ),
    CONSTRAINT ck_vtp_assessment CHECK (
        (assessed_by IS NULL AND assessed_at IS NULL) OR (assessed_by IS NOT NULL AND assessed_at IS NOT NULL)
    ),
    CONSTRAINT ck_vtp_approval CHECK (
        (status = 'DRAFT' AND approved_by IS NULL AND approved_at IS NULL AND profile_sha256 IS NULL)
        OR (status <> 'DRAFT' AND approved_by IS NOT NULL AND approved_at IS NOT NULL
            AND assessed_at IS NOT NULL AND profile_sha256 IS NOT NULL)
    ),
    INDEX idx_vtp_variant_state (variant_id, status, taxonomy_version_id, preparation_code),
    INDEX idx_vtp_taxonomy (taxonomy_version_id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
