-- ====================================================================
-- Migration V260917130000: Sửa lại bảng import_batches/import_batch_items
--   cho khớp với entity dùng chung (org.hvtra.commonservice.importing.*).
--
-- Lý do: V260917120001__create_import_tables.sql dùng CREATE TABLE IF NOT
--   EXISTS nên khi bảng import_batch_items đã tồn tại từ trước (thiếu cột),
--   câu lệnh không có tác dụng gì và bảng vẫn thiếu cột — Hibernate
--   (ddl-auto=validate) báo lỗi "missing column [created_at]".
--   Migration này bổ sung tường minh các cột còn thiếu bằng guard
--   information_schema (không đụng/sửa lại migration cũ), đồng thời tạo
--   bảng import_batch_sequences (bảng sinh batch_code dùng chung, entity
--   ImportBatchSequence) vì chưa có migration nào tạo bảng này ở
--   Inventory-Service.
-- ====================================================================

DROP PROCEDURE IF EXISTS fix_import_batch_tables_v1;

DELIMITER $$
CREATE PROCEDURE fix_import_batch_tables_v1()
BEGIN

    -- === import_batches ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batches' AND column_name='is_deleted') THEN
        ALTER TABLE import_batches ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batches' AND column_name='created_by') THEN
        ALTER TABLE import_batches ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batches' AND column_name='updated_by') THEN
        ALTER TABLE import_batches ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batches' AND column_name='created_at') THEN
        ALTER TABLE import_batches ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batches' AND column_name='updated_at') THEN
        ALTER TABLE import_batches ADD COLUMN updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batches' AND column_name='version') THEN
        ALTER TABLE import_batches ADD COLUMN version BIGINT NULL;
    END IF;

    -- === import_batch_items ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batch_items' AND column_name='occurrence_count') THEN
        ALTER TABLE import_batch_items ADD COLUMN occurrence_count INT NOT NULL DEFAULT 1 AFTER validation_errors;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batch_items' AND column_name='is_deleted') THEN
        ALTER TABLE import_batch_items ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batch_items' AND column_name='created_by') THEN
        ALTER TABLE import_batch_items ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batch_items' AND column_name='updated_by') THEN
        ALTER TABLE import_batch_items ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batch_items' AND column_name='created_at') THEN
        ALTER TABLE import_batch_items ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batch_items' AND column_name='updated_at') THEN
        ALTER TABLE import_batch_items ADD COLUMN updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_batch_items' AND column_name='version') THEN
        ALTER TABLE import_batch_items ADD COLUMN version BIGINT NULL;
    END IF;

END $$
DELIMITER ;

CALL fix_import_batch_tables_v1();
DROP PROCEDURE IF EXISTS fix_import_batch_tables_v1;

-- === import_batch_sequences (bảng sinh batch_code, chưa từng được tạo ở Inventory-Service) ===
CREATE TABLE IF NOT EXISTS import_batch_sequences (
    sequence_key VARCHAR(50) NOT NULL,
    current_value BIGINT NOT NULL,
    PRIMARY KEY (sequence_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
