-- ====================================================================
-- Migration V14: Thêm location_id vào INVENTORY_DOCUMENTS và tạo bảng 
--                INVENTORY_SUPPLEMENT_REQUESTS, INVENTORY_SUPPLIER_RETURN_REQUESTS
-- ====================================================================

DROP PROCEDURE IF EXISTS add_supplement_and_return_tables_v14;

DELIMITER $$
CREATE PROCEDURE add_supplement_and_return_tables_v14()
BEGIN

    -- 1. Bổ sung cột location_id vào inventory_documents nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'location_id'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN location_id BINARY(16) NULL AFTER branch_id;
        ALTER TABLE inventory_documents ADD INDEX idx_documents_location (location_id);
    END IF;

    -- Đồng bộ destination_location_id sang location_id nếu location_id đang NULL
    UPDATE inventory_documents 
    SET location_id = destination_location_id 
    WHERE location_id IS NULL AND destination_location_id IS NOT NULL;


    -- ============================================================
    -- 2. TẠO BẢNG INVENTORY_SUPPLEMENT_REQUESTS & LINES
    -- ============================================================
    CREATE TABLE IF NOT EXISTS inventory_supplement_requests (
        id BINARY(16) NOT NULL,
        request_code VARCHAR(100) NOT NULL,
        reference_type VARCHAR(50) NULL,
        reference_id VARCHAR(100) NULL,
        status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',
        note TEXT NULL,
        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
        created_by BINARY(16) NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        approved_by BINARY(16) NULL,
        approved_at DATETIME NULL,
        completed_at DATETIME NULL,
        PRIMARY KEY (id),
        UNIQUE KEY uq_supplement_requests_code (request_code),
        INDEX idx_supplement_requests_ref (reference_type, reference_id),
        INDEX idx_supplement_requests_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE IF NOT EXISTS inventory_supplement_request_lines (
        id BINARY(16) NOT NULL,
        request_id BINARY(16) NOT NULL,
        receipt_line_id BINARY(16) NULL,
        inventory_item_id BINARY(16) NOT NULL,
        quantity DECIMAL(18,2) NOT NULL DEFAULT 0,
        reason VARCHAR(255) NULL,
        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        INDEX idx_supplement_lines_request (request_id),
        INDEX idx_supplement_lines_item (inventory_item_id),
        CONSTRAINT fk_supplement_lines_request FOREIGN KEY (request_id) REFERENCES inventory_supplement_requests(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


    -- ============================================================
    -- 3. TẠO BẢNG INVENTORY_SUPPLIER_RETURN_REQUESTS & LINES
    -- ============================================================
    CREATE TABLE IF NOT EXISTS inventory_supplier_return_requests (
        id BINARY(16) NOT NULL,
        request_code VARCHAR(100) NOT NULL,
        reference_type VARCHAR(50) NULL,
        reference_id VARCHAR(100) NULL,
        supplier_id BINARY(16) NULL,
        status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',
        note TEXT NULL,
        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
        created_by BINARY(16) NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        approved_by BINARY(16) NULL,
        approved_at DATETIME NULL,
        completed_at DATETIME NULL,
        PRIMARY KEY (id),
        UNIQUE KEY uq_supplier_return_requests_code (request_code),
        INDEX idx_supplier_return_requests_ref (reference_type, reference_id),
        INDEX idx_supplier_return_requests_supplier (supplier_id),
        INDEX idx_supplier_return_requests_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE IF NOT EXISTS inventory_supplier_return_request_lines (
        id BINARY(16) NOT NULL,
        request_id BINARY(16) NOT NULL,
        receipt_line_id BINARY(16) NULL,
        inventory_item_id BINARY(16) NOT NULL,
        inventory_batch_id BINARY(16) NULL,
        quantity DECIMAL(18,2) NOT NULL DEFAULT 0,
        reason VARCHAR(255) NULL,
        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        INDEX idx_supplier_return_lines_request (request_id),
        INDEX idx_supplier_return_lines_item (inventory_item_id),
        CONSTRAINT fk_supplier_return_lines_request FOREIGN KEY (request_id) REFERENCES inventory_supplier_return_requests(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

END $$
DELIMITER ;

CALL add_supplement_and_return_tables_v14();
DROP PROCEDURE IF EXISTS add_supplement_and_return_tables_v14;
