-- ============================================================
-- Migration V20261104234700: Thay đổi kiểu cột source_file_name
-- từ VARCHAR(255) sang TEXT để đồng bộ với entity ImportBatch
-- (@Column columnDefinition = "TEXT") và tránh lỗi Data truncation.
-- ============================================================

ALTER TABLE import_batches MODIFY COLUMN source_file_name TEXT NULL;

