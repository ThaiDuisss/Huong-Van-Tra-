-- ====================================================================
-- Migration V260917150000: Xóa và tạo lại import_batches/import_batch_items
--   cho khớp hoàn toàn với entity dùng chung (org.hvtra.commonservice.importing.*).
--
-- Lý do: 2 bảng này ở inventory_db được tạo trước đó (có vẻ do chạy migration
--   không theo thứ tự — Flyway log báo "outOfOrder mode is active") với cấu
--   trúc không khớp: thiếu created_at, cột payload/validation_errors kiểu
--   JSON thay vì TEXT, các cột UUID như approved_by kiểu VARCHAR thay vì
--   BINARY(16)... Vá từng cột một (V260917130000, V260917140000) không xuể
--   vì còn nhiều cột UUID khác (submitted_by, rejected_by, created_by,
--   updated_by, import_batch_id, created_entity_id...) nhiều khả năng cũng
--   sai kiểu tương tự — và việc ALTER MODIFY VARCHAR→BINARY(16) không tự
--   chuyển đổi đúng giá trị UUID cũ.
--   Vì tính năng Import cho Inventory-Service (GoodsReceipt) CHƯA được
--   triển khai (chưa có handler/controller/FE nào ghi vào 2 bảng này ở
--   service này), 2 bảng đang chắc chắn KHÔNG có dữ liệu thật nào — an toàn
--   để xóa và tạo lại đúng từ đầu thay vì vá từng cột.
-- ====================================================================

DROP TABLE IF EXISTS import_batch_items;
DROP TABLE IF EXISTS import_batches;

CREATE TABLE import_batches (
    id BINARY(16) NOT NULL,
    import_type VARCHAR(50) NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    source_file_name VARCHAR(255) NULL,
    note VARCHAR(1000) NULL,
    submitted_by BINARY(16) NULL,
    submitted_at DATETIME NULL,
    approved_by BINARY(16) NULL,
    approved_at DATETIME NULL,
    rejected_by BINARY(16) NULL,
    rejected_at DATETIME NULL,
    rejection_reason VARCHAR(1000) NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BINARY(16) NULL,
    updated_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_import_batches_code (batch_code),
    INDEX idx_import_batches_type_status (import_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE import_batch_items (
    id BINARY(16) NOT NULL,
    import_batch_id BINARY(16) NOT NULL,
    row_index INT NOT NULL,
    payload TEXT NOT NULL,
    validation_errors TEXT NULL,
    occurrence_count INT NOT NULL DEFAULT 1,
    created_entity_id BINARY(16) NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BINARY(16) NULL,
    updated_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    PRIMARY KEY (id),
    INDEX idx_import_batch_items_batch (import_batch_id),
    CONSTRAINT fk_import_batch_items_batch FOREIGN KEY (import_batch_id) REFERENCES import_batches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
