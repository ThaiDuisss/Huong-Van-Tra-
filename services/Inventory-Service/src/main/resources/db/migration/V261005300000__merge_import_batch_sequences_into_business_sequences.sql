-- Gộp import_batch_sequences vào business_sequences
-- import_batch_sequences và business_sequences có schema giống hệt:
--   sequence_key VARCHAR(50) PRIMARY KEY
--   current_value BIGINT
-- Vì chúng phục vụ mục đích tương tự (sinh sequence dùng chung),
-- có thể gộp vào 1 bảng để quản lý tập trung.

-- Step 1: Sao chép dữ liệu từ import_batch_sequences sang business_sequences
--         (chỉ lấy những sequence_key chưa có ở business_sequences)
INSERT IGNORE INTO business_sequences (sequence_key, current_value)
SELECT sequence_key, current_value FROM import_batch_sequences;

-- Step 2: Xóa bảng import_batch_sequences
DROP TABLE IF EXISTS import_batch_sequences;
