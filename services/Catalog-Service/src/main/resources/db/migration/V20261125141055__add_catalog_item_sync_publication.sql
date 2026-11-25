CREATE TABLE catalog_item_sync_stream (
    message_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY
) ENGINE = InnoDB;

CREATE TABLE catalog_item_sync_publication (
    sequence_no BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    outbox_id BINARY(16) NOT NULL,
    message_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    available_at DATETIME(6) NOT NULL,
    claim_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(64) NULL,
    sent_at DATETIME(6) NULL,
    broker_partition INT NULL,
    broker_offset BIGINT NULL,
    UNIQUE KEY uk_catalog_item_sync_outbox (outbox_id),
    KEY idx_catalog_item_sync_due (state, available_at, sequence_no),
    KEY idx_catalog_item_sync_order (message_key, sequence_no, state),
    CONSTRAINT fk_catalog_item_sync_outbox FOREIGN KEY (outbox_id) REFERENCES outbox_events(id),
    CONSTRAINT fk_catalog_item_sync_stream FOREIGN KEY (message_key) REFERENCES catalog_item_sync_stream(message_key),
    CONSTRAINT ck_catalog_item_sync_state CHECK (state IN ('PENDING', 'IN_FLIGHT', 'RETRY', 'SENT', 'QUARANTINED'))
) ENGINE = InnoDB;

-- An empty table leaves the publisher fenced. Operators record an approved,
-- reconciled boundary after verifying that the old connector cannot publish.
CREATE TABLE catalog_item_sync_cutover (
    id TINYINT NOT NULL PRIMARY KEY,
    activation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mysql_server_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mysql_schema VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    send_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    reconciled_at DATETIME(6) NOT NULL,
    evidence_ref VARCHAR(255) NOT NULL,
    CONSTRAINT ck_catalog_item_sync_cutover_singleton CHECK (id = 1)
) ENGINE = InnoDB;
