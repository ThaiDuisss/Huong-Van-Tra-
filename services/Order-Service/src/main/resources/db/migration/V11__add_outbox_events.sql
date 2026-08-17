CREATE TABLE IF NOT EXISTS outbox_events (
                              id BINARY(16) PRIMARY KEY,

                              event_id VARCHAR(36) NOT NULL,
                              event_type VARCHAR(100) NOT NULL,
                              event_version INT NOT NULL DEFAULT 1,

                              aggregate_type VARCHAR(100) NOT NULL,
                              aggregate_id VARCHAR(36) NOT NULL,

                              message_key VARCHAR(100) NOT NULL,
                              business_key VARCHAR(150) NOT NULL,

                              topic VARCHAR(255) NOT NULL,
                              source_service VARCHAR(100) NOT NULL,

                              saga_id VARCHAR(36) NULL,
                              correlation_id VARCHAR(150) NULL,
                              causation_id VARCHAR(150) NULL,

                              payload JSON NOT NULL,
                              headers JSON NULL,

                              occurred_at DATETIME(6) NOT NULL,
                              created_at DATETIME(6) NOT NULL,
                              updated_at DATETIME(6) NULL,

                              UNIQUE KEY uk_outbox_event_id (event_id),
                              UNIQUE KEY uk_outbox_business_key (event_type, business_key),
                              INDEX idx_outbox_topic_created_at (topic, created_at),
                              INDEX idx_outbox_saga_id (saga_id),
                              INDEX idx_outbox_created_at (created_at)
);
