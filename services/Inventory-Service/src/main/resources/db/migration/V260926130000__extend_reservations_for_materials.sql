ALTER TABLE inventory_reservations
    ADD COLUMN order_item_id BINARY(16) NULL,
    ADD COLUMN ingredient_id BINARY(16) NULL,
    ADD COLUMN reservation_type VARCHAR(50) NOT NULL DEFAULT 'PRODUCT_VARIANT';

SET @old_unique_name := (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'inventory_reservations'
      AND non_unique = 0
      AND index_name <> 'PRIMARY'
    GROUP BY index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
        = 'order_id,product_variant_id,location_id'
    LIMIT 1
);

SET @drop_old_unique_sql := IF(
    @old_unique_name IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE inventory_reservations DROP INDEX ', @old_unique_name)
);

PREPARE drop_old_unique_stmt FROM @drop_old_unique_sql;
EXECUTE drop_old_unique_stmt;
DEALLOCATE PREPARE drop_old_unique_stmt;

ALTER TABLE inventory_reservations
    ADD CONSTRAINT uq_inventory_reservation_scope
        UNIQUE (order_id, product_variant_id, inventory_item_id, location_id, reservation_type);
