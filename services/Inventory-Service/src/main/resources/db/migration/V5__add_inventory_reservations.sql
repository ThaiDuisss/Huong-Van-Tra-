CREATE TABLE inventory_reservations (
    id BINARY(16) PRIMARY KEY,
    order_id BINARY(16) NOT NULL,
    product_variant_id BINARY(16) NOT NULL,
    inventory_item_id BINARY(16) NOT NULL,
    location_id BINARY(16) NOT NULL,
    quantity DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(50) NOT NULL,
    expires_at DATETIME NOT NULL,

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_inventory_reservations_order_variant_location (order_id, product_variant_id, location_id),
    CONSTRAINT fk_inventory_reservations_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
    CONSTRAINT fk_inventory_reservations_location FOREIGN KEY (location_id) REFERENCES inventory_locations(id),
    INDEX idx_inventory_reservations_order_status (order_id, status),
    INDEX idx_inventory_reservations_status_expires (status, expires_at)
);
