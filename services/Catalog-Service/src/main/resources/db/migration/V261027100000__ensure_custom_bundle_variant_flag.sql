-- ============================================================
-- Migration V261027100000: bật cờ custom_bundle cho variant hệ thống "CUSTOM-BUNDLE".
--   V261019100000 seed variant bằng WHERE NOT EXISTS (sku). Nếu variant đã có sẵn trước đó
--   (seed chạy tay trước khi có cột custom_bundle) thì INSERT bị bỏ qua và cờ giữ mặc định FALSE,
--   khiến Catalog không tìm thấy variant bundle.
-- ============================================================

UPDATE product_variants
SET custom_bundle = TRUE
WHERE sku = 'CUSTOM-BUNDLE'
  AND custom_bundle = FALSE;
