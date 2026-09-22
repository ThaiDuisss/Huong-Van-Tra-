-- ============================================================
-- Migration: Đảm bảo version không bị NULL cho các bảng trong Inventory-Service
-- Tránh lỗi NullPointerException khi Hibernate increment @Version
-- ============================================================

UPDATE inventory_balances SET version = 0 WHERE version IS NULL;
UPDATE inventory_items SET version = 0 WHERE version IS NULL;
UPDATE inventory_locations SET version = 0 WHERE version IS NULL;
UPDATE inventory_replenishment_alerts SET version = 0 WHERE version IS NULL;
UPDATE inventory_replenishment_policies SET version = 0 WHERE version IS NULL;
UPDATE inventory_reservations SET version = 0 WHERE version IS NULL;
UPDATE inventory_reservation_batch_allocations SET version = 0 WHERE version IS NULL;
UPDATE inventory_counts SET version = 0 WHERE version IS NULL;
UPDATE inventory_count_lines SET version = 0 WHERE version IS NULL;
UPDATE inventory_quality_inspections SET version = 0 WHERE version IS NULL;
UPDATE inventory_quality_inspection_lines SET version = 0 WHERE version IS NULL;
UPDATE inventory_documents SET version = 0 WHERE version IS NULL;
UPDATE inventory_document_lines SET version = 0 WHERE version IS NULL;
UPDATE inventory_document_batch_allocations SET version = 0 WHERE version IS NULL;
UPDATE inventory_transactions SET version = 0 WHERE version IS NULL;
UPDATE inventory_transaction_lines SET version = 0 WHERE version IS NULL;
UPDATE inventory_transaction_batch_allocations SET version = 0 WHERE version IS NULL;
UPDATE inventory_batches SET version = 0 WHERE version IS NULL;
UPDATE inventory_batch_relations SET version = 0 WHERE version IS NULL;
UPDATE inventory_supplement_requests SET version = 0 WHERE version IS NULL;
UPDATE inventory_supplement_request_lines SET version = 0 WHERE version IS NULL;
UPDATE inventory_supplier_return_requests SET version = 0 WHERE version IS NULL;
UPDATE inventory_supplier_return_request_lines SET version = 0 WHERE version IS NULL;
UPDATE production_orders SET version = 0 WHERE version IS NULL;
UPDATE import_batches SET version = 0 WHERE version IS NULL;
