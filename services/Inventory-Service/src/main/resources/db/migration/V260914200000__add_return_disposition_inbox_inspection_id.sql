ALTER TABLE inbox_events
    ADD COLUMN inspection_id VARCHAR(36) NULL AFTER business_payload_hash,
    ADD KEY idx_inbox_inspection_id (inspection_id);
