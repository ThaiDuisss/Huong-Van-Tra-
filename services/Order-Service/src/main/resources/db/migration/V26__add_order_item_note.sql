ALTER TABLE order_items
    ADD COLUMN note VARCHAR(1000) NULL AFTER bom_version_id;
