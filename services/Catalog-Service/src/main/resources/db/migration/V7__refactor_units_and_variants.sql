-- ============================================================
-- Migration V7: Refactor Units, ProductVariants, Ingredients (Gộp Bản Chuẩn BINARY(16))
-- Catalog-Service
-- ============================================================

-- ------------------------------------------------------------
-- STEP 1: Thêm tách biệt từng cột và cấu hình chuẩn BINARY(16)
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS AddColumnSafely;
DELIMITER //
CREATE PROCEDURE AddColumnSafely()
BEGIN
    -- 1. Thêm cột type cho units
    IF NOT EXISTS (SELECT * FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'units' AND column_name = 'type') THEN
ALTER TABLE units ADD COLUMN `type` VARCHAR(20) NOT NULL DEFAULT 'BASE' AFTER precision_scale;
END IF;

    -- 2. Thêm cột base_unit_id cho units chuẩn BINARY(16)
    IF NOT EXISTS (SELECT * FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'units' AND column_name = 'base_unit_id') THEN
ALTER TABLE units ADD COLUMN base_unit_id BINARY(16) NULL AFTER `type`;
END IF;

    -- 3. Thêm cột conversion_rate cho units
    IF NOT EXISTS (SELECT * FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'units' AND column_name = 'conversion_rate') THEN
ALTER TABLE units ADD COLUMN conversion_rate DECIMAL(18, 4) NOT NULL DEFAULT 1.0000 AFTER base_unit_id;
END IF;

    -- 4. Thêm cột unit_id cho product_variants chuẩn BINARY(16)
    IF NOT EXISTS (SELECT * FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product_variants' AND column_name = 'unit_id') THEN
ALTER TABLE product_variants ADD COLUMN unit_id BINARY(16) NULL AFTER cost_price;
END IF;
END //
DELIMITER ;

CALL AddColumnSafely();
DROP PROCEDURE IF EXISTS AddColumnSafely;


-- ------------------------------------------------------------
-- STEP 2: Tạo ràng buộc Khóa ngoại và Index chuẩn BINARY(16) cho bảng units
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS AddUnitsFKSafely;
DELIMITER //
CREATE PROCEDURE AddUnitsFKSafely()
BEGIN
    -- Ép lại kiểu dữ liệu của base_unit_id về BINARY(16) phòng khi lần chạy trước lỡ tạo bằng CHAR
ALTER TABLE units MODIFY COLUMN base_unit_id BINARY(16) NULL;

-- Thêm khóa ngoại nếu chưa tồn tại
IF NOT EXISTS (SELECT * FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'units' AND constraint_name = 'fk_units_base_unit') THEN
ALTER TABLE units ADD CONSTRAINT fk_units_base_unit FOREIGN KEY (base_unit_id) REFERENCES units(id) ON DELETE SET NULL;
END IF;
END //
DELIMITER ;

CALL AddUnitsFKSafely();
DROP PROCEDURE IF EXISTS AddUnitsFKSafely;


-- ------------------------------------------------------------
-- STEP 3: Migrate dữ liệu & Dọn dẹp bảng product_variants / ingredients
-- ------------------------------------------------------------
-- Đồng bộ dữ liệu tạm thời
UPDATE product_variants SET unit_id = base_unit_id WHERE base_unit_id IS NOT NULL AND unit_id IS NULL;

DROP PROCEDURE IF EXISTS DropOldColumnsAndAddFKSafely;
DELIMITER //
CREATE PROCEDURE DropOldColumnsAndAddFKSafely()
BEGIN
    -- 1. Xóa các ràng buộc cũ của product_variants
    IF EXISTS (SELECT * FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'product_variants' AND constraint_name = 'fk_product_variants_unit') THEN
ALTER TABLE product_variants DROP FOREIGN KEY fk_product_variants_unit;
END IF;

    -- 2. Xóa các cột cũ của product_variants
    IF EXISTS (SELECT * FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product_variants' AND column_name = 'pack_quantity') THEN
ALTER TABLE product_variants DROP COLUMN pack_quantity;
END IF;

    IF EXISTS (SELECT * FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product_variants' AND column_name = 'base_unit_id') THEN
ALTER TABLE product_variants DROP COLUMN base_unit_id;
END IF;

    -- 3. Cấu hình lại cột unit_id thành NOT NULL và tạo Khóa ngoại mới
ALTER TABLE product_variants MODIFY COLUMN unit_id BINARY(16) NOT NULL;

IF NOT EXISTS (SELECT * FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'product_variants' AND constraint_name = 'fk_product_variants_unit') THEN
ALTER TABLE product_variants ADD CONSTRAINT fk_product_variants_unit FOREIGN KEY (unit_id) REFERENCES units(id);
END IF;

    -- 4. Dọn dẹp bảng ingredients
    IF EXISTS (SELECT * FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'ingredients' AND constraint_name = 'fk_ingredients_unit') THEN
ALTER TABLE ingredients DROP FOREIGN KEY fk_ingredients_unit;
END IF;

    IF EXISTS (SELECT * FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ingredients' AND column_name = 'base_unit_id') THEN
ALTER TABLE ingredients DROP COLUMN base_unit_id;
END IF;
END //
DELIMITER ;

CALL DropOldColumnsAndAddFKSafely;
DROP PROCEDURE IF EXISTS DropOldColumnsAndAddFKSafely;