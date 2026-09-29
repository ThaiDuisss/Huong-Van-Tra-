-- ============================================================
-- Migration: Add ingredients.type (0 = packaging / non-recipe material, 1 = recipe ingredient)
-- The column may already exist on some environments (added manually on VPS),
-- so it is only created when missing.
-- Target Database: MySQL 8
-- ============================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ingredients'
      AND COLUMN_NAME = 'type'
);

SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE ingredients ADD COLUMN type INT NOT NULL DEFAULT 1 AFTER reorder_point',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
