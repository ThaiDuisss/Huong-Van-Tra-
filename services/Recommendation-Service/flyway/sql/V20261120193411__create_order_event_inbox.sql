CREATE TABLE order_event_streams (
    order_id VARCHAR(36) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_event_inbox (
    event_id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    topic VARCHAR(249) NOT NULL,
    partition_id INT NOT NULL,
    record_offset BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    key_version VARCHAR(64) NOT NULL,
    receipt JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(48) NULL,
    observed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_order_inbox_stream FOREIGN KEY (order_id) REFERENCES order_event_streams (order_id),
    INDEX idx_order_inbox_pending (order_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_event_deliveries (
    topic VARCHAR(249) NOT NULL,
    partition_id INT NOT NULL,
    record_offset BIGINT NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (topic, partition_id, record_offset),
    CONSTRAINT fk_order_delivery_inbox FOREIGN KEY (event_id) REFERENCES order_event_inbox (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_return_lines (
    return_item_id VARCHAR(36) NOT NULL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    return_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    order_item_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NULL,
    product_variant_id VARCHAR(36) NOT NULL,
    purchase_type VARCHAR(24) NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_order_return_inbox FOREIGN KEY (event_id) REFERENCES order_event_inbox (event_id),
    CONSTRAINT fk_order_return_stream FOREIGN KEY (order_id) REFERENCES order_event_streams (order_id),
    INDEX idx_order_return_lines_order (order_id, order_item_id),
    INDEX idx_order_return_lines_history (customer_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
