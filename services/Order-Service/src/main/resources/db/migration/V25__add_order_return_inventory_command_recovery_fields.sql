ALTER TABLE order_return_inventory_commands
    ADD COLUMN not_found_count INT NOT NULL DEFAULT 0 AFTER inspection_id,
    ADD COLUMN last_not_found_at DATETIME(6) NULL AFTER not_found_count,
    ADD COLUMN recovery_attempt_count INT NOT NULL DEFAULT 0 AFTER last_not_found_at,
    ADD COLUMN last_recovery_at DATETIME(6) NULL AFTER recovery_attempt_count;
