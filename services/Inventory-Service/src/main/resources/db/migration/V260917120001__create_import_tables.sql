-- ============================================================
-- Migration V260917100000: Cơ chế Import Excel dạng Draft → Duyệt.
--   import_batches: 1 phiếu import (1 lần upload Excel).
--   import_batch_items: từng dòng/entity trong phiếu, lưu payload JSON
--     + lỗi validate JSON, chỉ ghi vào bảng nghiệp vụ thật khi được duyệt.
-- ============================================================

CREATE TABLE IF NOT EXISTS import_batches (
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

CREATE TABLE IF NOT EXISTS import_batch_items (
                                                  id BINARY(16) NOT NULL,
    import_batch_id BINARY(16) NOT NULL,
    row_index INT NOT NULL,
    payload TEXT NOT NULL,
    validation_errors TEXT NULL,
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
