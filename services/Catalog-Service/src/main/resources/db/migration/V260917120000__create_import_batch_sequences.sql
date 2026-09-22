-- ============================================================
-- Migration V260917120000: Bảng sinh mã import_batch_sequences.
--   Engine ImportBatch/ImportBatchService đã chuyển sang module dùng chung
--   (common/Common-Service) để Inventory-Service tái sử dụng sau này — vì mỗi
--   service có DB riêng, không thể tiếp tục dùng chung bảng business_sequences
--   (chỉ có trong catalog_db) để sinh batch_code. Bảng mới này trung lập, mỗi
--   service tự tạo trong DB của mình.
--   Seed lại đúng giá trị đếm hiện tại từ business_sequences (key IMPORT_BATCH)
--   để không bị nhảy số/trùng batch_code với các phiếu import đã tạo trước đó.
-- ============================================================

CREATE TABLE IF NOT EXISTS import_batch_sequences (
    sequence_key VARCHAR(50) NOT NULL,
    current_value BIGINT NOT NULL,
    PRIMARY KEY (sequence_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO import_batch_sequences (sequence_key, current_value)
SELECT 'IMPORT_BATCH', current_value FROM business_sequences WHERE sequence_key = 'IMPORT_BATCH'
ON DUPLICATE KEY UPDATE current_value = VALUES(current_value);

INSERT INTO import_batch_sequences (sequence_key, current_value)
SELECT 'IMPORT_BATCH', 0
WHERE NOT EXISTS (SELECT 1 FROM import_batch_sequences WHERE sequence_key = 'IMPORT_BATCH');
