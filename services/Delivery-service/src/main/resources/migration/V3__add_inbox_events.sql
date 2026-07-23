
CREATE TABLE IF NOT EXISTS inbox_events (
                                            id BINARY(16) PRIMARY KEY,

    event_id VARCHAR(36) NOT NULL,
    consumer_group VARCHAR(150) NOT NULL,

    topic VARCHAR(150) NOT NULL,
    partition_no INT NULL,
    offset_no BIGINT NULL,
    message_key VARCHAR(150) NULL,

    event_type VARCHAR(100) NOT NULL,
    event_version INT NOT NULL DEFAULT 1,

    source_service VARCHAR(100) NULL,
    entity_type VARCHAR(100) NULL,
    entity_id VARCHAR(36) NULL,

    aggregate_type VARCHAR(100) NULL,
    aggregate_id VARCHAR(36) NULL,

    saga_id VARCHAR(36) NULL,
    correlation_id VARCHAR(150) NULL,

    payload_json LONGTEXT NULL,
    headers_json TEXT NULL,

    status VARCHAR(30) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 3,
    received_at DATETIME(6) NOT NULL,

    processed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    last_error TEXT NULL,

    UNIQUE KEY uk_inbox_event_consumer (event_id, consumer_group),
    INDEX idx_inbox_saga_id (saga_id),
    INDEX idx_inbox_status (status),
    INDEX idx_inbox_topic_offset (topic, partition_no, offset_no)
    );
