-- ============================================================
-- Migration: Chuyển barcode vào bảng product_variants, tự động sinh
--   mã vạch cho các bản ghi chưa có và xóa bảng barcodes
-- ============================================================

DROP PROCEDURE IF EXISTS migrate_move_barcode_v20261101204300;

DELIMITER $$
CREATE PROCEDURE migrate_move_barcode_v20261101204300()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE var_id BINARY(16);
    DECLARE cur_seq BIGINT;
    DECLARE base12 VARCHAR(12);
    DECLARE chk INT;
    DECLARE sum_val INT;
    DECLARE new_barcode VARCHAR(13);

    DECLARE cur CURSOR FOR
        SELECT id FROM product_variants WHERE barcode IS NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    -- 1. Thêm cột barcode nếu chưa tồn tại
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'product_variants' AND column_name = 'barcode'
    ) THEN
        ALTER TABLE product_variants ADD COLUMN barcode VARCHAR(100) NULL;
    END IF;

    -- 2. Sao chép dữ liệu barcode sang product_variants nếu bảng barcodes còn tồn tại
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'barcodes') THEN
        UPDATE product_variants pv
        JOIN (
            SELECT b.product_variant_id, b.barcode_value
            FROM barcodes b
            WHERE b.is_primary = true
            UNION
            SELECT b1.product_variant_id, b1.barcode_value
            FROM barcodes b1
            WHERE NOT EXISTS (
                SELECT 1 FROM barcodes b2
                WHERE b2.product_variant_id = b1.product_variant_id AND b2.is_primary = true
            )
        ) src ON src.product_variant_id = pv.id
        SET pv.barcode = src.barcode_value
        WHERE pv.barcode IS NULL;
    END IF;

    -- 3. Khởi tạo sequence key BARCODE trong business_sequences nếu chưa có
    INSERT INTO business_sequences (sequence_key, current_value)
    VALUES ('BARCODE', 0)
    ON DUPLICATE KEY UPDATE current_value = current_value;

    SELECT current_value INTO cur_seq
    FROM business_sequences
    WHERE sequence_key = 'BARCODE'
    FOR UPDATE;

    IF cur_seq IS NULL THEN
        SET cur_seq = 0;
    END IF;

    -- 4. Tự động sinh barcode theo công thức EAN-13 (893 + sequence 9 chữ số + check digit) cho các variant có barcode NULL
    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO var_id;
        IF done THEN
            LEAVE read_loop;
        END IF;

        SET cur_seq = cur_seq + 1;
        SET base12 = CONCAT('893', LPAD(cur_seq, 9, '0'));

        -- Tính check digit EAN-13:
        SET sum_val =
            CAST(SUBSTRING(base12, 1, 1) AS UNSIGNED) * 1 +
            CAST(SUBSTRING(base12, 2, 1) AS UNSIGNED) * 3 +
            CAST(SUBSTRING(base12, 3, 1) AS UNSIGNED) * 1 +
            CAST(SUBSTRING(base12, 4, 1) AS UNSIGNED) * 3 +
            CAST(SUBSTRING(base12, 5, 1) AS UNSIGNED) * 1 +
            CAST(SUBSTRING(base12, 6, 1) AS UNSIGNED) * 3 +
            CAST(SUBSTRING(base12, 7, 1) AS UNSIGNED) * 1 +
            CAST(SUBSTRING(base12, 8, 1) AS UNSIGNED) * 3 +
            CAST(SUBSTRING(base12, 9, 1) AS UNSIGNED) * 1 +
            CAST(SUBSTRING(base12, 10, 1) AS UNSIGNED) * 3 +
            CAST(SUBSTRING(base12, 11, 1) AS UNSIGNED) * 1 +
            CAST(SUBSTRING(base12, 12, 1) AS UNSIGNED) * 3;

        SET chk = (10 - (sum_val % 10)) % 10;
        SET new_barcode = CONCAT(base12, chk);

        UPDATE product_variants SET barcode = new_barcode WHERE id = var_id;
    END LOOP;
    CLOSE cur;

    UPDATE business_sequences SET current_value = cur_seq WHERE sequence_key = 'BARCODE';

    -- 5. Tạo chỉ mục unique cho barcode trên bảng product_variants nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'product_variants' AND index_name = 'uq_product_variants_barcode'
    ) THEN
        CREATE UNIQUE INDEX uq_product_variants_barcode ON product_variants (barcode);
    END IF;

    -- 6. Xóa bảng barcodes
    DROP TABLE IF EXISTS barcodes;

END$$
DELIMITER ;

CALL migrate_move_barcode_v20261101204300();
DROP PROCEDURE IF EXISTS migrate_move_barcode_v20261101204300;
