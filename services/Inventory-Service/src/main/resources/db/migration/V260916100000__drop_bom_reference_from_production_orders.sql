-- ====================================================================
-- Migration V260916100000: Bỏ tham chiếu BOM riêng lẻ khỏi production_orders
--   Lý do: Catalog-Service đã bỏ bảng "boms" (1 product-variant giờ chỉ còn
--   đúng 1 danh sách nguyên liệu bom_items), nên production_orders không
--   còn cần lưu bom_id/bom_version — danh sách nguyên liệu được materialize
--   trực tiếp vào production_order_lines tại thời điểm tạo lệnh.
-- ====================================================================

ALTER TABLE production_orders
    DROP COLUMN bom_id,
    DROP COLUMN bom_version;
