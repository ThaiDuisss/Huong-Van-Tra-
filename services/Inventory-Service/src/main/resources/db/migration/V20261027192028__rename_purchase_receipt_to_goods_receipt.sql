-- Thống nhất tên phiếu nhập NCC: PURCHASE_RECEIPT -> GOODS_RECEIPT trong mọi cột lưu
-- InventoryDocumentType, InventoryReferenceType và InventoryTransactionType.
UPDATE inventory_documents SET document_type = 'GOODS_RECEIPT' WHERE document_type = 'PURCHASE_RECEIPT';
UPDATE inventory_documents SET reference_type = 'GOODS_RECEIPT' WHERE reference_type = 'PURCHASE_RECEIPT';

UPDATE inventory_transactions SET type = 'GOODS_RECEIPT' WHERE type = 'PURCHASE_RECEIPT';
UPDATE inventory_transactions SET reference_type = 'GOODS_RECEIPT' WHERE reference_type = 'PURCHASE_RECEIPT';

UPDATE inventory_batches SET source_type = 'GOODS_RECEIPT' WHERE source_type = 'PURCHASE_RECEIPT';
UPDATE inventory_batch_relations SET reference_type = 'GOODS_RECEIPT' WHERE reference_type = 'PURCHASE_RECEIPT';
