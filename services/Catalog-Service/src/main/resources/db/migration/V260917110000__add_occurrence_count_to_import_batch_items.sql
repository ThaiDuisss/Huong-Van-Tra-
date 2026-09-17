-- ============================================================
-- Migration V260917110000: Thêm occurrence_count cho import_batch_items.
--   Khi parse Excel, các dòng trùng lặp y hệt nhau ở TẤT CẢ các trường sẽ được
--   gộp lại thành 1 item duy nhất; occurrence_count ghi lại số lần dòng đó
--   xuất hiện trong file gốc để trả về FE hiển thị cảnh báo trùng lặp.
-- ============================================================

ALTER TABLE import_batch_items
    ADD COLUMN occurrence_count INT NOT NULL DEFAULT 1 AFTER validation_errors;
