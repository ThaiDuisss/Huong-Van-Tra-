CREATE TABLE IF NOT EXISTS order_return_history (
    id BINARY(16) NOT NULL,
    return_id BINARY(16) NOT NULL,
    action VARCHAR(50) NOT NULL,
    from_status VARCHAR(50) NULL,
    to_status VARCHAR(50) NULL,
    actor_user_id BINARY(16) NULL,
    note VARCHAR(500) NULL,
    reference_type VARCHAR(50) NULL,
    reference_id VARCHAR(100) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_by BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by BINARY(16) NULL,
    updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_return_history_return
        FOREIGN KEY (return_id) REFERENCES order_returns(id)
        ON DELETE RESTRICT,
    UNIQUE KEY uk_order_return_history_action (return_id, action),
    INDEX idx_order_return_history_return (return_id, occurred_at),
    INDEX idx_order_return_history_action (action),
    INDEX idx_order_return_history_actor (actor_user_id)
);
