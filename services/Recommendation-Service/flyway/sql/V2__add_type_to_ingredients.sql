-- Add ingredients.type synced from Catalog-Service.
-- 0 = packaging / non-recipe material (excluded from training), 1 = recipe ingredient.
-- Created only when missing so environments that already have the column keep working.

SET @col_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ingredients'
      AND COLUMN_NAME = 'type'
);

SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE ingredients ADD COLUMN type INT NULL AFTER status',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
