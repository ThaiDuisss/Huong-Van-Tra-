-- Migration V9: Add reference_code to inventory_items
ALTER TABLE inventory_items
    ADD COLUMN reference_code VARCHAR(100) NULL AFTER reference_id;

CREATE INDEX idx_inventory_items_reference_code ON inventory_items(reference_code);
