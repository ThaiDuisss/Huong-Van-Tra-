-- ============================================================
-- Migration V261019100000: Đóng trà theo yêu cầu (Custom Tea Bundle).
--   1. Giá bán lẻ theo đơn vị cho nguyên liệu (null = không bán lẻ trong bundle).
--   2. Cờ custom_bundle cho product_variants: đánh dấu variant hệ thống dùng làm dòng bundle
--      trong đơn bán, ẩn khỏi danh sách sản phẩm bán thường.
--   3. Seed danh mục + đơn vị "Gói" + sản phẩm + variant hệ thống "Trà đóng theo yêu cầu".
--      Dùng id cố định và WHERE NOT EXISTS để chạy lại an toàn.
-- ============================================================

ALTER TABLE ingredients
    ADD COLUMN retail_price_per_unit DECIMAL(18, 2) NULL AFTER reorder_point;

ALTER TABLE product_variants
    ADD COLUMN custom_bundle BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO categories (id, category_code, name, status, version, is_deleted)
SELECT UUID_TO_BIN('6b1c2f7e-3a4d-4e5f-8a90-0c0b0d0e0f01'), 'CUSTOM-BUNDLE', 'Trà đóng theo yêu cầu', 'ACTIVE', 0, FALSE
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_code = 'CUSTOM-BUNDLE');

INSERT INTO units (id, name, symbol, precision_scale, `type`, conversion_rate, status, version, is_deleted)
SELECT UUID_TO_BIN('6b1c2f7e-3a4d-4e5f-8a90-0c0b0d0e0f02'), 'Gói', 'gói', 0, 'BASE', 1.0000, 'ACTIVE', 0, FALSE
WHERE NOT EXISTS (SELECT 1 FROM units WHERE LOWER(name) = 'gói' AND is_deleted = FALSE);

INSERT INTO products (id, category_id, product_code, name, description, status, version, is_deleted)
SELECT UUID_TO_BIN('6b1c2f7e-3a4d-4e5f-8a90-0c0b0d0e0f03'),
       (SELECT id FROM categories WHERE category_code = 'CUSTOM-BUNDLE'),
       'CUSTOM-BUNDLE', 'Trà đóng theo yêu cầu',
       'Sản phẩm hệ thống: dòng bundle khách tự chọn nguyên liệu. Không chỉnh sửa hoặc xoá.',
       'ACTIVE', 0, FALSE
WHERE NOT EXISTS (SELECT 1 FROM products WHERE product_code = 'CUSTOM-BUNDLE');

INSERT INTO product_variants (id, product_id, sku, variant_name, quantity, selling_price, cost_price, unit_id,
                              inventory_policy, status, sellable_quantity, production_threshold,
                              production_batch_quantity, custom_bundle, version, is_deleted)
SELECT UUID_TO_BIN('6b1c2f7e-3a4d-4e5f-8a90-0c0b0d0e0f04'),
       (SELECT id FROM products WHERE product_code = 'CUSTOM-BUNDLE'),
       'CUSTOM-BUNDLE', 'Trà đóng theo yêu cầu', 1, 0, 0,
       (SELECT id FROM units WHERE LOWER(name) = 'gói' AND is_deleted = FALSE ORDER BY created_at LIMIT 1),
       'DO_NOT_TRACK', 'ACTIVE', 0, 0, 1, TRUE, 0, FALSE
WHERE NOT EXISTS (SELECT 1 FROM product_variants WHERE sku = 'CUSTOM-BUNDLE');
