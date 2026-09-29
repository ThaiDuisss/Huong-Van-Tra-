ALTER TABLE inventory_reservations
    ADD COLUMN processing_attempt INT NOT NULL DEFAULT 0;

ALTER TABLE inventory_reservations
    DROP INDEX uq_inventory_reservation_scope,
    ADD CONSTRAINT uq_inventory_reservation_scope
        UNIQUE (order_id, product_variant_id, inventory_item_id, location_id, reservation_type, processing_attempt);
