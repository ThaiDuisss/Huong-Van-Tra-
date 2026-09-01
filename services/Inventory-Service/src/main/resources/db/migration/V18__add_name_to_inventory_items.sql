-- ============================================================
-- Migration V18: Thêm cột name vào bảng inventory_items
-- ============================================================

DROP PROCEDURE IF EXISTS add_name_to_inventory_items_v18;

DELIMITER $$
CREATE PROCEDURE add_name_to_inventory_items_v18()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_items' 
          AND column_name = 'name'
    ) THEN
        ALTER TABLE inventory_items ADD COLUMN name VARCHAR(255) NULL AFTER reference_code;
    END IF;
END $$
DELIMITER ;

CALL add_name_to_inventory_items_v18();
DROP PROCEDURE IF EXISTS add_name_to_inventory_items_v18;
