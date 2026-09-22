CREATE TABLE IF NOT EXISTS notifications (
    id BINARY(16) NOT NULL,
    user_id VARCHAR(36) NULL,
    customer_id VARCHAR(36) NULL,
    branch_id VARCHAR(36) NULL,
    type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    source_event_id VARCHAR(150) NOT NULL,
    source_event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NULL,
    aggregate_id VARCHAR(36) NULL,
    order_id VARCHAR(36) NULL,
    order_code VARCHAR(50) NULL,
    return_id VARCHAR(36) NULL,
    return_code VARCHAR(50) NULL,
    payment_id VARCHAR(36) NULL,
    refund_transaction_id VARCHAR(36) NULL,
    actor_user_id VARCHAR(36) NULL,
    saga_id VARCHAR(36) NULL,
    status VARCHAR(30) NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_source_event (source_event_id),
    INDEX idx_notifications_user_status (user_id, status, created_at),
    INDEX idx_notifications_customer_status (customer_id, status, created_at),
    INDEX idx_notifications_order_id (order_id),
    INDEX idx_notifications_return_id (return_id),
    INDEX idx_notifications_created_at (created_at)
);

DROP PROCEDURE IF EXISTS add_notification_service_v2_columns;

DELIMITER //
CREATE PROCEDURE add_notification_service_v2_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notifications' AND column_name = 'branch_id'
    ) THEN
        ALTER TABLE notifications ADD COLUMN branch_id VARCHAR(36) NULL AFTER customer_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notifications' AND column_name = 'return_id'
    ) THEN
        ALTER TABLE notifications ADD COLUMN return_id VARCHAR(36) NULL AFTER order_code;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notifications' AND column_name = 'return_code'
    ) THEN
        ALTER TABLE notifications ADD COLUMN return_code VARCHAR(50) NULL AFTER return_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notifications' AND column_name = 'refund_transaction_id'
    ) THEN
        ALTER TABLE notifications ADD COLUMN refund_transaction_id VARCHAR(36) NULL AFTER payment_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notifications' AND column_name = 'actor_user_id'
    ) THEN
        ALTER TABLE notifications ADD COLUMN actor_user_id VARCHAR(36) NULL AFTER refund_transaction_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'event_id'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN event_id VARCHAR(150) NULL AFTER id;
        UPDATE inbox_events SET event_id = message_id WHERE event_id IS NULL AND message_id IS NOT NULL;
        ALTER TABLE inbox_events MODIFY COLUMN event_id VARCHAR(150) NOT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'consumer_group'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN consumer_group VARCHAR(150) NOT NULL DEFAULT 'Notification-Service-order-events' AFTER event_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'topic'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN topic VARCHAR(150) NOT NULL DEFAULT 'unknown' AFTER consumer_group;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'partition_no'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN partition_no INT NULL AFTER topic;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'offset_no'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN offset_no BIGINT NULL AFTER partition_no;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'message_key'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN message_key VARCHAR(150) NULL AFTER offset_no;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'event_version'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN event_version INT NOT NULL DEFAULT 1 AFTER event_type;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'source_service'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN source_service VARCHAR(100) NULL AFTER event_version;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'entity_type'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN entity_type VARCHAR(100) NULL AFTER source_service;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'entity_id'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN entity_id VARCHAR(36) NULL AFTER entity_type;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'payload_json'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN payload_json LONGTEXT NULL AFTER correlation_id;
        UPDATE inbox_events SET payload_json = payload WHERE payload_json IS NULL AND payload IS NOT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'headers_json'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN headers_json TEXT NULL AFTER payload_json;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'max_retry'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN max_retry INT NOT NULL DEFAULT 3 AFTER retry_count;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND column_name = 'last_error'
    ) THEN
        ALTER TABLE inbox_events ADD COLUMN last_error TEXT NULL;
        UPDATE inbox_events SET last_error = error_message WHERE last_error IS NULL AND error_message IS NOT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND index_name = 'uk_notification_inbox_event_id'
    ) THEN
        ALTER TABLE inbox_events ADD UNIQUE KEY uk_notification_inbox_event_id (event_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'inbox_events' AND index_name = 'uk_notification_inbox_event_consumer'
    ) THEN
        ALTER TABLE inbox_events ADD UNIQUE KEY uk_notification_inbox_event_consumer (event_id, consumer_group);
    END IF;
END //
DELIMITER ;

CALL add_notification_service_v2_columns();

DROP PROCEDURE IF EXISTS add_notification_service_v2_columns;
