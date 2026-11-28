-- Chuyển chính sách ngưỡng tồn từ từng location cụ thể sang theo loại vị trí (WAREHOUSE / SHELF)
ALTER TABLE inventory_replenishment_policies
    ADD COLUMN location_type VARCHAR(50) NOT NULL DEFAULT 'WAREHOUSE' AFTER inventory_item_id;

SET @fk_name := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inventory_replenishment_policies'
      AND COLUMN_NAME = 'location_id'
      AND REFERENCED_TABLE_NAME IS NOT NULL
    LIMIT 1
);
SET @drop_fk_sql := IF(@fk_name IS NOT NULL,
    CONCAT('ALTER TABLE inventory_replenishment_policies DROP FOREIGN KEY `', @fk_name, '`'),
    'SELECT 1');
PREPARE stmt_fk FROM @drop_fk_sql;
EXECUTE stmt_fk;
DEALLOCATE PREPARE stmt_fk;

SET @uq_name := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inventory_replenishment_policies'
      AND CONSTRAINT_TYPE = 'UNIQUE'
      AND CONSTRAINT_NAME = 'uq_replenishment_policy_item_loc'
    LIMIT 1
);
SET @drop_uq_sql := IF(@uq_name IS NOT NULL,
    'ALTER TABLE inventory_replenishment_policies DROP INDEX uq_replenishment_policy_item_loc',
    'SELECT 1');
PREPARE stmt_uq FROM @drop_uq_sql;
EXECUTE stmt_uq;
DEALLOCATE PREPARE stmt_uq;

ALTER TABLE inventory_replenishment_policies
    DROP COLUMN location_id,
    ADD CONSTRAINT uq_replenishment_policy_item_type UNIQUE (inventory_item_id, location_type);
