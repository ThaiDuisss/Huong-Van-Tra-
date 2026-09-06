CREATE TABLE order_returns (
    id BINARY(16) NOT NULL,
    return_code VARCHAR(50) NOT NULL,
    order_id BINARY(16) NOT NULL,
    status VARCHAR(50) NOT NULL,
    note VARCHAR(500) NULL,
    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_returns_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
            ON DELETE RESTRICT,
    UNIQUE KEY uk_order_returns_return_code (return_code),
    INDEX idx_order_returns_order_id (order_id),
    INDEX idx_order_returns_status (status),
    INDEX idx_order_returns_created_at (created_at)
);

CREATE TABLE order_return_items (
    id BINARY(16) NOT NULL,
    return_id BINARY(16) NOT NULL,
    order_item_id BINARY(16) NOT NULL,
    quantity DECIMAL(14, 3) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    product_condition VARCHAR(50) NOT NULL,
    note VARCHAR(500) NULL,
    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_return_items_return
        FOREIGN KEY (return_id) REFERENCES order_returns(id)
            ON DELETE CASCADE,
    CONSTRAINT fk_order_return_items_order_item
        FOREIGN KEY (order_item_id) REFERENCES order_items(id)
            ON DELETE RESTRICT,
    INDEX idx_order_return_items_return_id (return_id),
    INDEX idx_order_return_items_order_item_id (order_item_id),
    INDEX idx_order_return_items_created_at (created_at)
);
