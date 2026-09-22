CREATE TABLE IF NOT EXISTS order_return_inventory_commands (
    id BINARY(16) NOT NULL,
    return_id BINARY(16) NOT NULL,
    command_id BINARY(16) NOT NULL,
    status VARCHAR(50) NOT NULL,
    requested_by BINARY(16) NULL,
    created_by BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by BINARY(16) NULL,
    updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_return_inventory_commands_return
        FOREIGN KEY (return_id) REFERENCES order_returns(id)
        ON DELETE RESTRICT,
    UNIQUE KEY uk_order_return_inventory_commands_command_id (command_id),
    INDEX idx_order_return_inventory_commands_return (return_id),
    INDEX idx_order_return_inventory_commands_status (status)
);

CREATE TABLE IF NOT EXISTS order_return_inventory_command_lines (
    id BINARY(16) NOT NULL,
    inventory_command_id BINARY(16) NOT NULL,
    return_item_id BINARY(16) NOT NULL,
    order_item_id BINARY(16) NOT NULL,
    product_variant_id BINARY(16) NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    result VARCHAR(50) NOT NULL,
    destination_location_id BINARY(16) NULL,
    note VARCHAR(500) NULL,
    created_by BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by BINARY(16) NULL,
    updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_return_inventory_command_lines_command
        FOREIGN KEY (inventory_command_id) REFERENCES order_return_inventory_commands(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_order_return_inventory_command_lines_return_item
        FOREIGN KEY (return_item_id) REFERENCES order_return_items(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_order_return_inventory_command_lines_order_item
        FOREIGN KEY (order_item_id) REFERENCES order_items(id)
        ON DELETE RESTRICT,
    INDEX idx_order_return_inventory_command_lines_command (inventory_command_id),
    INDEX idx_order_return_inventory_command_lines_return_item (return_item_id)
);
