ALTER TABLE inbox_events
    ADD COLUMN command_id VARCHAR(36) NULL AFTER event_id,
    ADD COLUMN business_payload_hash VARCHAR(64) NULL AFTER command_id;

ALTER TABLE inbox_events
    ADD UNIQUE KEY uk_inbox_command_consumer (command_id, consumer_group),
    ADD KEY idx_inbox_command_id (command_id);
