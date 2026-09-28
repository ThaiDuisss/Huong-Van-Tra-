ALTER TABLE product_variants
    ADD COLUMN sellable_quantity DECIMAL(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN production_threshold DECIMAL(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN production_batch_quantity DECIMAL(18, 2) NOT NULL DEFAULT 1;
