-- ====================================================================
-- Migration V15: Bổ sung các cột audit còn thiếu vào bảng
--                inventory_supplement_request_lines
--                (created_by, updated_by, version)
-- ====================================================================

DROP PROCEDURE IF EXISTS fix_audit_cols_supplement_lines_v15;

DELIMITER $$
CREATE PROCEDURE fix_audit_cols_supplement_lines_v15()
BEGIN

    -- created_by
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name   = 'inventory_supplement_request_lines'
          AND column_name  = 'created_by'
    ) THEN
        ALTER TABLE inventory_supplement_request_lines
            ADD COLUMN created_by BINARY(16) NULL
            AFTER is_deleted;
    END IF;

    -- updated_by
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name   = 'inventory_supplement_request_lines'
          AND column_name  = 'updated_by'
    ) THEN
        ALTER TABLE inventory_supplement_request_lines
            ADD COLUMN updated_by BINARY(16) NULL
            AFTER created_by;
    END IF;

    -- version (optimistic locking)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name   = 'inventory_supplement_request_lines'
          AND column_name  = 'version'
    ) THEN
        ALTER TABLE inventory_supplement_request_lines
            ADD COLUMN version BIGINT NULL
            AFTER updated_at;
    END IF;

END $$
DELIMITER ;

CALL fix_audit_cols_supplement_lines_v15();
DROP PROCEDURE IF EXISTS fix_audit_cols_supplement_lines_v15;
