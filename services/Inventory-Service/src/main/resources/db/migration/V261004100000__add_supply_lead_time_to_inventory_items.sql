-- Thời gian trung bình để có một mặt hàng, lưu sẵn để ước tính ngày có hàng không phải quét lịch sử mỗi lần:
--   thành phẩm  = thời gian sản xuất (tạo lệnh -> hoàn tất)
--   nguyên liệu = thời gian nhập hàng (lập phiếu nhập -> ghi sổ)
-- Được tính lại khi lệnh sản xuất hoàn tất / phiếu nhập ghi sổ. NULL = chưa có lịch sử.
ALTER TABLE inventory_items
    ADD COLUMN avg_supply_lead_seconds BIGINT NULL,
    ADD COLUMN supply_lead_samples INT NOT NULL DEFAULT 0;

UPDATE inventory_items
SET avg_supply_lead_seconds = (
        SELECT ROUND(AVG(TIMESTAMPDIFF(SECOND, p.created_at, p.completed_at)))
        FROM production_orders p
        WHERE p.product_variant_id = inventory_items.reference_id
          AND p.status = 'COMPLETED'
          AND p.is_deleted = FALSE
          AND p.completed_at > p.created_at),
    supply_lead_samples = (
        SELECT COUNT(*)
        FROM production_orders p
        WHERE p.product_variant_id = inventory_items.reference_id
          AND p.status = 'COMPLETED'
          AND p.is_deleted = FALSE
          AND p.completed_at > p.created_at)
WHERE item_type = 'PRODUCT_VARIANT';

UPDATE inventory_items
SET avg_supply_lead_seconds = (
        SELECT ROUND(AVG(TIMESTAMPDIFF(SECOND, d.created_at, d.posted_at)))
        FROM inventory_documents d
        WHERE d.document_type = 'PURCHASE_RECEIPT'
          AND d.status = 'POSTED'
          AND d.is_deleted = FALSE
          AND d.posted_at > d.created_at
          AND EXISTS (SELECT 1 FROM inventory_document_lines l
                      WHERE l.document_id = d.id AND l.inventory_item_id = inventory_items.id)),
    supply_lead_samples = (
        SELECT COUNT(*)
        FROM inventory_documents d
        WHERE d.document_type = 'PURCHASE_RECEIPT'
          AND d.status = 'POSTED'
          AND d.is_deleted = FALSE
          AND d.posted_at > d.created_at
          AND EXISTS (SELECT 1 FROM inventory_document_lines l
                      WHERE l.document_id = d.id AND l.inventory_item_id = inventory_items.id))
WHERE item_type = 'INGREDIENT';
