-- ====================================================================
-- Migration V260917140000: Sửa kiểu cột payload/validation_errors của
--   import_batch_items từ JSON sang TEXT cho khớp với entity dùng chung
--   (ImportBatchItem.payload/validationErrors khai báo @Lob columnDefinition="TEXT").
--
-- Lý do: bảng import_batch_items ở Inventory-Service đã tồn tại từ trước
--   với cột payload/validation_errors kiểu JSON (khác với TEXT mà các
--   migration V260917120001/V260917130000 mong muốn) — Hibernate
--   (ddl-auto=validate) báo lỗi "wrong column type ... found [json],
--   but expecting [text]". MODIFY COLUMN sang TEXT không mất dữ liệu vì
--   JSON trong MySQL vẫn lưu dạng text, chỉ đổi kiểu khai báo cột.
-- ====================================================================

ALTER TABLE import_batch_items MODIFY COLUMN payload TEXT NOT NULL;
ALTER TABLE import_batch_items MODIFY COLUMN validation_errors TEXT NULL;
