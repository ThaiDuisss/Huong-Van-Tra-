CREATE TABLE taste_catalog_projection (
    source_key VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    projection_revision BIGINT UNSIGNED NOT NULL,
    source_read_at DATETIME(6) NOT NULL,
    synced_at DATETIME(6) NOT NULL,
    payload_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload JSON NOT NULL,
    CONSTRAINT ck_tcp_revision CHECK (projection_revision > 0),
    CONSTRAINT ck_tcp_payload_object CHECK (JSON_TYPE(payload) = 'OBJECT')
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
