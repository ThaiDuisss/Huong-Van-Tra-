-- ============================================================
-- Migration V12: Xóa cột base_unit_id ở bảng inventory_items
-- ============================================================

DROP PROCEDURE IF EXISTS drop_base_unit_id_v12;

DELIMITER $$
CREATE PROCEDURE drop_base_unit_id_v12()
BEGIN
    -- 1. Đảm bảo cột unit_id tồn tại
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_items' 
          AND column_name = 'unit_id'
    ) THEN
        ALTER TABLE inventory_items ADD COLUMN unit_id BINARY(16) NULL AFTER item_type;
    END IF;

    -- 2. Nếu có cột base_unit_id thì copy dữ liệu sang unit_id nếu unit_id đang NULL
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_items' 
          AND column_name = 'base_unit_id'
    ) THEN
        UPDATE inventory_items 
        SET unit_id = base_unit_id 
        WHERE unit_id IS NULL AND base_unit_id IS NOT NULL;

        -- Drop foreign key trên base_unit_id nếu có
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints 
            WHERE table_schema = DATABASE() 
              AND table_name = 'inventory_items' 
              AND constraint_name = 'fk_inventory_items_base_unit'
        ) THEN
            ALTER TABLE inventory_items DROP FOREIGN KEY fk_inventory_items_base_unit;
        END IF;

        -- Drop index trên base_unit_id nếu có
        IF EXISTS (
            SELECT 1 FROM information_schema.statistics 
            WHERE table_schema = DATABASE() 
              AND table_name = 'inventory_items' 
              AND index_name = 'idx_inventory_items_base_unit'
        ) THEN
            ALTER TABLE inventory_items DROP INDEX idx_inventory_items_base_unit;
        END IF;

        -- Drop column base_unit_id
        ALTER TABLE inventory_items DROP COLUMN base_unit_id;
    END IF;

    -- 3. Tạo index cho unit_id nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_items' 
          AND index_name = 'idx_inventory_items_unit'
    ) THEN
        ALTER TABLE inventory_items ADD INDEX idx_inventory_items_unit (unit_id);
    END IF;

END $$
DELIMITER ;

CALL drop_base_unit_id_v12();
DROP PROCEDURE IF EXISTS drop_base_unit_id_v12;
