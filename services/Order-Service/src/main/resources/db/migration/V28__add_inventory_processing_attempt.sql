ALTER TABLE orders
    ADD COLUMN inventory_processing_attempt INT NOT NULL DEFAULT 0;
