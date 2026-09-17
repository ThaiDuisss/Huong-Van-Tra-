-- ============================================================
-- Migration V260917090000: Thêm định lượng (quantity) vào product_variants.
--   Tên Mặt hàng (variant_name) từ nay được hệ thống tự sinh theo công thức
--   "Tên sản phẩm + Định lượng + Tên đơn vị", không cho người dùng tự nhập.
-- ============================================================

ALTER TABLE product_variants
    ADD COLUMN quantity DECIMAL(18,2) NOT NULL DEFAULT 0 AFTER variant_name;
