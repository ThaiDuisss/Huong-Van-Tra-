-- Tính lại thời gian cung ứng của thành phẩm theo quy trình mới:
--   thành phẩm = thời gian sản xuất (tạo lệnh -> duyệt lệnh; COMPLETED/completed_at đã bị bỏ khỏi quy trình)
-- Nguyên liệu (phiếu nhập -> ghi sổ) giữ nguyên như V261004100000.
UPDATE inventory_items
SET avg_supply_lead_seconds = (
        SELECT ROUND(AVG(TIMESTAMPDIFF(SECOND, p.created_at, p.approved_at)))
        FROM production_orders p
        WHERE (p.product_variant_id = inventory_items.id OR p.product_variant_id = inventory_items.reference_id)
          AND p.status IN ('APPROVED', 'COMPLETED')
          AND p.is_deleted = FALSE
          AND p.approved_at > p.created_at),
    supply_lead_samples = (
        SELECT COUNT(*)
        FROM production_orders p
        WHERE (p.product_variant_id = inventory_items.id OR p.product_variant_id = inventory_items.reference_id)
          AND p.status IN ('APPROVED', 'COMPLETED')
          AND p.is_deleted = FALSE
          AND p.approved_at > p.created_at)
WHERE item_type = 'PRODUCT_VARIANT';
