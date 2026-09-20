-- ============================================================
-- Migration V260916110000: Bỏ khái niệm nhiều BOM/variant (bảng boms),
--   1 product-variant chỉ còn đúng 1 danh sách nguyên liệu (bom_items)
--   gắn trực tiếp vào product_variant_id.
-- Chiến lược giữ dữ liệu cũ: với mỗi variant đang có nhiều BOM,
--   giữ lại dòng nguyên liệu của BOM có is_default = TRUE;
--   nếu không có BOM mặc định thì giữ BOM có version cao nhất.
--
-- Viết dạng AN TOÀN KHI CHẠY LẠI (idempotent): MySQL không rollback
--   được DDL giữa chừng, nên nếu một lần chạy trước lỗi ở bước sau,
--   các bước trước (ví dụ ADD COLUMN) đã commit — chạy lại từ đầu phải
--   không bị lỗi "Duplicate column" / "already exists" ở những bước đó.
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_boms_to_variant_bom_items_v260916110000;

DELIMITER $$
CREATE PROCEDURE migrate_boms_to_variant_bom_items_v260916110000()
BEGIN

    -- 1. Thêm cột product_variant_id nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'bom_items' AND column_name = 'product_variant_id'
    ) THEN
        ALTER TABLE bom_items ADD COLUMN product_variant_id BINARY(16) NULL AFTER bom_id;
    END IF;

    -- 2 & 3. Backfill product_variant_id từ bảng boms và chỉ giữ lại dòng
    --        của BOM được chọn (is_default trước, version cao nhất sau) —
    --        chỉ chạy nếu bảng boms còn tồn tại (đã DROP thì bỏ qua).
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'boms') THEN
        UPDATE bom_items bi
        JOIN boms b ON bi.bom_id = b.id
        SET bi.product_variant_id = b.product_variant_id
        WHERE bi.product_variant_id IS NULL;

        DROP TEMPORARY TABLE IF EXISTS tmp_chosen_boms;
        CREATE TEMPORARY TABLE tmp_chosen_boms AS
        SELECT product_variant_id, id AS chosen_bom_id
        FROM (
            SELECT id, product_variant_id,
                   ROW_NUMBER() OVER (PARTITION BY product_variant_id ORDER BY is_default DESC, version DESC) AS rn
            FROM boms
        ) ranked
        WHERE rn = 1;

        DELETE bi FROM bom_items bi
        JOIN tmp_chosen_boms cb ON bi.product_variant_id = cb.product_variant_id
        WHERE bi.bom_id IS NOT NULL AND bi.bom_id <> cb.chosen_bom_id;

        DROP TEMPORARY TABLE IF EXISTS tmp_chosen_boms;
    END IF;

    -- 4. Bắt buộc NOT NULL cho product_variant_id nếu đang cho phép NULL
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'bom_items'
          AND column_name = 'product_variant_id' AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE bom_items MODIFY COLUMN product_variant_id BINARY(16) NOT NULL;
    END IF;

    -- 5. Thêm FK / index / unique constraint nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'bom_items' AND constraint_name = 'fk_bom_items_variant'
    ) THEN
        ALTER TABLE bom_items ADD CONSTRAINT fk_bom_items_variant
            FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'bom_items' AND index_name = 'idx_bom_items_variant'
    ) THEN
        ALTER TABLE bom_items ADD INDEX idx_bom_items_variant (product_variant_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'bom_items' AND constraint_name = 'uq_bom_items_variant_ingredient'
    ) THEN
        ALTER TABLE bom_items ADD CONSTRAINT uq_bom_items_variant_ingredient
            UNIQUE (product_variant_id, ingredient_id);
    END IF;

    -- 6. Bỏ FK và cột bom_id nếu còn tồn tại
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'bom_items' AND constraint_name = 'fk_bom_items_bom'
    ) THEN
        ALTER TABLE bom_items DROP FOREIGN KEY fk_bom_items_bom;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'bom_items' AND column_name = 'bom_id'
    ) THEN
        ALTER TABLE bom_items DROP COLUMN bom_id;
    END IF;

    -- 7. Xóa bảng boms nếu còn tồn tại
    DROP TABLE IF EXISTS boms;

END $$
DELIMITER ;

CALL migrate_boms_to_variant_bom_items_v260916110000();
DROP PROCEDURE IF EXISTS migrate_boms_to_variant_bom_items_v260916110000;
