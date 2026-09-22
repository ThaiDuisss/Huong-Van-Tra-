-- ============================================================
-- Migration: Đảm bảo version không bị NULL cho các bảng trong Catalog-Service
-- Tránh lỗi NullPointerException khi Hibernate increment @Version
-- ============================================================

UPDATE categories SET version = 0 WHERE version IS NULL;
UPDATE products SET version = 0 WHERE version IS NULL;
UPDATE product_variants SET version = 0 WHERE version IS NULL;
UPDATE units SET version = 0 WHERE version IS NULL;
UPDATE barcodes SET version = 0 WHERE version IS NULL;
UPDATE suppliers SET version = 0 WHERE version IS NULL;
UPDATE ingredients SET version = 0 WHERE version IS NULL;
UPDATE bom_items SET version = 0 WHERE version IS NULL;
UPDATE price_lists SET version = 0 WHERE version IS NULL;
UPDATE price_list_items SET version = 0 WHERE version IS NULL;
UPDATE import_batches SET version = 0 WHERE version IS NULL;
