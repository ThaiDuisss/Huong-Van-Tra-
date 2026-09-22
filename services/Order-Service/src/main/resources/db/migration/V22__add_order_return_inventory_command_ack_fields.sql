ALTER TABLE order_return_inventory_commands
    ADD COLUMN processed_at DATETIME(6) NULL AFTER status,
    ADD COLUMN failed_at DATETIME(6) NULL AFTER processed_at,
    ADD COLUMN failure_code VARCHAR(100) NULL AFTER failed_at,
    ADD COLUMN failure_reason VARCHAR(500) NULL AFTER failure_code,
    ADD COLUMN inspection_id BINARY(16) NULL AFTER failure_reason;

ALTER TABLE order_return_inventory_commands
    ADD INDEX idx_order_return_inventory_commands_inspection (inspection_id);
