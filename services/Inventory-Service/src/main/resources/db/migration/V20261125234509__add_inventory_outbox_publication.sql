CREATE TABLE inventory_outbox_publication (
    outbox_id BINARY(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    available_at DATETIME(6) NOT NULL,
    claim_token CHAR(36) NULL,
    attempts INT NOT NULL DEFAULT 0,
    sent_at DATETIME(6) NULL,
    last_error VARCHAR(64) NULL,

    PRIMARY KEY (outbox_id),
    CONSTRAINT fk_inventory_outbox_publication_event
        FOREIGN KEY (outbox_id) REFERENCES outbox_events (id),
    INDEX idx_inventory_outbox_publication_due (state, available_at, outbox_id)
);
