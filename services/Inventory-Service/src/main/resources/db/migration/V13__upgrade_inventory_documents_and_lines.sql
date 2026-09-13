-- ====================================================================
-- Migration V13: Nâng cấp bảng INVENTORY_DOCUMENTS, INVENTORY_DOCUMENT_LINES,
--                và INVENTORY_DOCUMENT_BATCH_ALLOCATIONS
-- ====================================================================

DROP PROCEDURE IF EXISTS upgrade_inventory_docs_v13;

DELIMITER $$
CREATE PROCEDURE upgrade_inventory_docs_v13()
BEGIN

    -- ============================================================
    -- 1. BẢNG INVENTORY_DOCUMENTS: Bổ sung các trường quản lý quy trình
    -- ============================================================
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'warehouse_id'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN warehouse_id BINARY(16) NULL AFTER branch_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'supplier_id'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN supplier_id BINARY(16) NULL AFTER warehouse_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'expected_date'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN expected_date DATETIME NULL AFTER reference_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'approval_note'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN approval_note TEXT NULL AFTER approved_at;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'rejected_by'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN rejected_by BINARY(16) NULL AFTER approval_note;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'rejected_at'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN rejected_at DATETIME NULL AFTER rejected_by;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'rejection_reason'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN rejection_reason TEXT NULL AFTER rejected_at;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'received_by'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN received_by BINARY(16) NULL AFTER rejection_reason;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'received_at'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN received_at DATETIME NULL AFTER received_by;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'qc_by'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN qc_by BINARY(16) NULL AFTER received_at;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'qc_at'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN qc_at DATETIME NULL AFTER qc_by;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND column_name = 'posted_by'
    ) THEN
        ALTER TABLE inventory_documents ADD COLUMN posted_by BINARY(16) NULL AFTER qc_at;
    END IF;

    -- Index cho warehouse_id, supplier_id, expected_date
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND index_name = 'idx_documents_warehouse'
    ) THEN
        ALTER TABLE inventory_documents ADD INDEX idx_documents_warehouse (warehouse_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_documents' AND index_name = 'idx_documents_supplier'
    ) THEN
        ALTER TABLE inventory_documents ADD INDEX idx_documents_supplier (supplier_id);
    END IF;


    -- ============================================================
    -- 2. BẢNG INVENTORY_DOCUMENT_LINES: Chuyển đổi các cột số lượng
    -- ============================================================
    -- ordered_quantity
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'ordered_quantity'
    ) THEN
        ALTER TABLE inventory_document_lines ADD COLUMN ordered_quantity DECIMAL(18,2) NOT NULL DEFAULT 0;
        -- Nếu có requested_quantity cũ thì sao chép sang ordered_quantity
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'requested_quantity'
        ) THEN
            UPDATE inventory_document_lines SET ordered_quantity = requested_quantity;
        END IF;
    END IF;

    -- received_quantity
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'received_quantity'
    ) THEN
        ALTER TABLE inventory_document_lines ADD COLUMN received_quantity DECIMAL(18,2) NOT NULL DEFAULT 0;
        -- Nếu có processed_quantity cũ thì sao chép sang received_quantity
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'processed_quantity'
        ) THEN
            UPDATE inventory_document_lines SET received_quantity = processed_quantity;
        END IF;
    END IF;

    -- shortage_quantity
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'shortage_quantity'
    ) THEN
        ALTER TABLE inventory_document_lines ADD COLUMN shortage_quantity DECIMAL(18,2) NOT NULL DEFAULT 0;
    END IF;

    -- excess_quantity
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'excess_quantity'
    ) THEN
        ALTER TABLE inventory_document_lines ADD COLUMN excess_quantity DECIMAL(18,2) NOT NULL DEFAULT 0;
    END IF;

    -- accepted_quantity
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'accepted_quantity'
    ) THEN
        ALTER TABLE inventory_document_lines ADD COLUMN accepted_quantity DECIMAL(18,2) NOT NULL DEFAULT 0;
        -- Tạm thời gán accepted_quantity = received_quantity
        UPDATE inventory_document_lines SET accepted_quantity = received_quantity WHERE accepted_quantity = 0;
    END IF;

    -- rejected_quantity
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'rejected_quantity'
    ) THEN
        ALTER TABLE inventory_document_lines ADD COLUMN rejected_quantity DECIMAL(18,2) NOT NULL DEFAULT 0;
    END IF;

    -- Xóa các cột cũ nếu còn tồn tại
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'requested_quantity'
    ) THEN
        ALTER TABLE inventory_document_lines DROP COLUMN requested_quantity;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_lines' AND column_name = 'processed_quantity'
    ) THEN
        ALTER TABLE inventory_document_lines DROP COLUMN processed_quantity;
    END IF;


    -- ============================================================
    -- 3. BẢNG INVENTORY_DOCUMENT_BATCH_ALLOCATIONS: Đảm bảo FK & Index
    -- ============================================================
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_batch_allocations' AND index_name = 'idx_doc_batch_alloc_line'
    ) THEN
        ALTER TABLE inventory_document_batch_allocations ADD INDEX idx_doc_batch_alloc_line (document_line_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() AND table_name = 'inventory_document_batch_allocations' AND index_name = 'idx_doc_batch_alloc_batch'
    ) THEN
        ALTER TABLE inventory_document_batch_allocations ADD INDEX idx_doc_batch_alloc_batch (inventory_batch_id);
    END IF;

END $$
DELIMITER ;

CALL upgrade_inventory_docs_v13();
DROP PROCEDURE IF EXISTS upgrade_inventory_docs_v13;
