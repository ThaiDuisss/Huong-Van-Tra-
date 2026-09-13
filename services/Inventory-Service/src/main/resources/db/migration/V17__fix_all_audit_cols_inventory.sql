-- ====================================================================
-- Migration V17: Đảm bảo đủ cột audit (created_by, updated_by, version)
--               cho tất cả bảng trong Inventory-Service
-- Các bảng được kiểm tra:
--   inventory_balances, inventory_items, inventory_locations,
--   inventory_replenishment_alerts, inventory_replenishment_policies,
--   inventory_reservations, inventory_reservation_batch_allocations,
--   inventory_counts, inventory_count_lines,
--   inventory_quality_inspections, inventory_quality_inspection_lines,
--   inventory_documents, inventory_document_lines,
--   inventory_document_batch_allocations,
--   inventory_transactions, inventory_transaction_lines,
--   inventory_transaction_batch_allocations,
--   inventory_batches, inventory_batch_relations,
--   inventory_supplement_requests,
--   inventory_supplier_return_requests,
--   inventory_supplement_request_lines     (đã fix V15/V16 nhưng guard lại)
--   inventory_supplier_return_request_lines (đã fix V16 nhưng guard lại)
-- ====================================================================

DROP PROCEDURE IF EXISTS fix_all_audit_cols_v17;

DELIMITER $$
CREATE PROCEDURE fix_all_audit_cols_v17()
BEGIN

    -- ----------------------------------------------------------------
    -- Helper: thêm cột nếu chưa có
    -- ----------------------------------------------------------------

    -- === inventory_balances ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_balances' AND column_name='created_by') THEN
        ALTER TABLE inventory_balances ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_balances' AND column_name='updated_by') THEN
        ALTER TABLE inventory_balances ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_balances' AND column_name='version') THEN
        ALTER TABLE inventory_balances ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_items ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_items' AND column_name='created_by') THEN
        ALTER TABLE inventory_items ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_items' AND column_name='updated_by') THEN
        ALTER TABLE inventory_items ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_items' AND column_name='version') THEN
        ALTER TABLE inventory_items ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_locations ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_locations' AND column_name='created_by') THEN
        ALTER TABLE inventory_locations ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_locations' AND column_name='updated_by') THEN
        ALTER TABLE inventory_locations ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_locations' AND column_name='version') THEN
        ALTER TABLE inventory_locations ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_replenishment_alerts ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_replenishment_alerts' AND column_name='created_by') THEN
        ALTER TABLE inventory_replenishment_alerts ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_replenishment_alerts' AND column_name='updated_by') THEN
        ALTER TABLE inventory_replenishment_alerts ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_replenishment_alerts' AND column_name='version') THEN
        ALTER TABLE inventory_replenishment_alerts ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_replenishment_policies ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_replenishment_policies' AND column_name='created_by') THEN
        ALTER TABLE inventory_replenishment_policies ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_replenishment_policies' AND column_name='updated_by') THEN
        ALTER TABLE inventory_replenishment_policies ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_replenishment_policies' AND column_name='version') THEN
        ALTER TABLE inventory_replenishment_policies ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_reservations ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_reservations' AND column_name='created_by') THEN
        ALTER TABLE inventory_reservations ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_reservations' AND column_name='updated_by') THEN
        ALTER TABLE inventory_reservations ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_reservations' AND column_name='version') THEN
        ALTER TABLE inventory_reservations ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_reservation_batch_allocations ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_reservation_batch_allocations' AND column_name='created_by') THEN
        ALTER TABLE inventory_reservation_batch_allocations ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_reservation_batch_allocations' AND column_name='updated_by') THEN
        ALTER TABLE inventory_reservation_batch_allocations ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_reservation_batch_allocations' AND column_name='version') THEN
        ALTER TABLE inventory_reservation_batch_allocations ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_counts ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_counts' AND column_name='created_by') THEN
        ALTER TABLE inventory_counts ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_counts' AND column_name='updated_by') THEN
        ALTER TABLE inventory_counts ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_counts' AND column_name='version') THEN
        ALTER TABLE inventory_counts ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_count_lines ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_count_lines' AND column_name='created_by') THEN
        ALTER TABLE inventory_count_lines ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_count_lines' AND column_name='updated_by') THEN
        ALTER TABLE inventory_count_lines ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_count_lines' AND column_name='version') THEN
        ALTER TABLE inventory_count_lines ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_quality_inspections ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_quality_inspections' AND column_name='created_by') THEN
        ALTER TABLE inventory_quality_inspections ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_quality_inspections' AND column_name='updated_by') THEN
        ALTER TABLE inventory_quality_inspections ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_quality_inspections' AND column_name='version') THEN
        ALTER TABLE inventory_quality_inspections ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_quality_inspection_lines ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_quality_inspection_lines' AND column_name='created_by') THEN
        ALTER TABLE inventory_quality_inspection_lines ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_quality_inspection_lines' AND column_name='updated_by') THEN
        ALTER TABLE inventory_quality_inspection_lines ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_quality_inspection_lines' AND column_name='version') THEN
        ALTER TABLE inventory_quality_inspection_lines ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_documents ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_documents' AND column_name='created_by') THEN
        ALTER TABLE inventory_documents ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_documents' AND column_name='updated_by') THEN
        ALTER TABLE inventory_documents ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_documents' AND column_name='version') THEN
        ALTER TABLE inventory_documents ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_document_lines ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_document_lines' AND column_name='created_by') THEN
        ALTER TABLE inventory_document_lines ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_document_lines' AND column_name='updated_by') THEN
        ALTER TABLE inventory_document_lines ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_document_lines' AND column_name='version') THEN
        ALTER TABLE inventory_document_lines ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_document_batch_allocations ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_document_batch_allocations' AND column_name='created_by') THEN
        ALTER TABLE inventory_document_batch_allocations ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_document_batch_allocations' AND column_name='updated_by') THEN
        ALTER TABLE inventory_document_batch_allocations ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_document_batch_allocations' AND column_name='version') THEN
        ALTER TABLE inventory_document_batch_allocations ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_transactions ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_transactions' AND column_name='created_by') THEN
        ALTER TABLE inventory_transactions ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_transactions' AND column_name='updated_by') THEN
        ALTER TABLE inventory_transactions ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_transactions' AND column_name='version') THEN
        ALTER TABLE inventory_transactions ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_transaction_lines ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_transaction_lines' AND column_name='created_by') THEN
        ALTER TABLE inventory_transaction_lines ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_transaction_lines' AND column_name='updated_by') THEN
        ALTER TABLE inventory_transaction_lines ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_transaction_lines' AND column_name='version') THEN
        ALTER TABLE inventory_transaction_lines ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_transaction_batch_allocations ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_transaction_batch_allocations' AND column_name='created_by') THEN
        ALTER TABLE inventory_transaction_batch_allocations ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_transaction_batch_allocations' AND column_name='updated_by') THEN
        ALTER TABLE inventory_transaction_batch_allocations ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_transaction_batch_allocations' AND column_name='version') THEN
        ALTER TABLE inventory_transaction_batch_allocations ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_batch_relations ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_batch_relations' AND column_name='created_by') THEN
        ALTER TABLE inventory_batch_relations ADD COLUMN created_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_batch_relations' AND column_name='updated_by') THEN
        ALTER TABLE inventory_batch_relations ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_batch_relations' AND column_name='version') THEN
        ALTER TABLE inventory_batch_relations ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_supplement_requests ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplement_requests' AND column_name='updated_by') THEN
        ALTER TABLE inventory_supplement_requests ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplement_requests' AND column_name='version') THEN
        ALTER TABLE inventory_supplement_requests ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_supplement_request_lines (guard) ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplement_request_lines' AND column_name='created_by') THEN
        ALTER TABLE inventory_supplement_request_lines ADD COLUMN created_by BINARY(16) NULL AFTER is_deleted;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplement_request_lines' AND column_name='updated_by') THEN
        ALTER TABLE inventory_supplement_request_lines ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplement_request_lines' AND column_name='version') THEN
        ALTER TABLE inventory_supplement_request_lines ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_supplier_return_requests ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplier_return_requests' AND column_name='updated_by') THEN
        ALTER TABLE inventory_supplier_return_requests ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplier_return_requests' AND column_name='version') THEN
        ALTER TABLE inventory_supplier_return_requests ADD COLUMN version BIGINT NULL;
    END IF;

    -- === inventory_supplier_return_request_lines (guard) ===
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplier_return_request_lines' AND column_name='created_by') THEN
        ALTER TABLE inventory_supplier_return_request_lines ADD COLUMN created_by BINARY(16) NULL AFTER is_deleted;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplier_return_request_lines' AND column_name='updated_by') THEN
        ALTER TABLE inventory_supplier_return_request_lines ADD COLUMN updated_by BINARY(16) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inventory_supplier_return_request_lines' AND column_name='version') THEN
        ALTER TABLE inventory_supplier_return_request_lines ADD COLUMN version BIGINT NULL;
    END IF;

END $$
DELIMITER ;

CALL fix_all_audit_cols_v17();
DROP PROCEDURE IF EXISTS fix_all_audit_cols_v17;
