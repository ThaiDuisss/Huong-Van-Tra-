ALTER TABLE inventory_items
    ADD COLUMN reorder_point INT NOT NULL DEFAULT 10;