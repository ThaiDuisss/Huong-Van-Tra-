-- ====================================================================
-- Migration V20261118071540: Tách inventory_item_id thành 2 FK rõ ràng
--   trong bảng inventory_reservations
--
-- Trước: inventory_item_id BINARY(16) NOT NULL — dùng chung cho cả
--        PRODUCT_VARIANT reservation lẫn MATERIAL reservation.
--
-- Sau:
--   inventory_item_product_id    — FK → inventory_items (loại PRODUCT_VARIANT)
--                                   NULL khi reservation là MATERIAL
--   inventory_item_ingredient_id — FK → inventory_items (loại INGREDIENT)
--                                  NULL khi reservation là PRODUCT_VARIANT
--
-- product_variant_id và ingredient_id giữ nguyên là catalog reference UUID
-- (không phải FK nội bộ inventory).
-- ====================================================================

-- Bước 1: Thêm 2 cột mới (nullable để migrate dữ liệu trước)
ALTER TABLE inventory_reservations
    ADD COLUMN inventory_item_product_id    BINARY(16) NULL AFTER inventory_item_id,
    ADD COLUMN inventory_item_ingredient_id BINARY(16) NULL AFTER inventory_item_product_id;

-- Bước 2: Copy dữ liệu từ inventory_item_id sang đúng cột tương ứng
--   PRODUCT_VARIANT reservation → inventory_item_product_id
UPDATE inventory_reservations
SET inventory_item_product_id = inventory_item_id
WHERE reservation_type = 'PRODUCT_VARIANT';

--   MATERIAL reservation → inventory_item_ingredient_id
UPDATE inventory_reservations
SET inventory_item_ingredient_id = inventory_item_id
WHERE reservation_type = 'MATERIAL';

-- Bước 3: Drop unique constraint cũ (dùng inventory_item_id)
ALTER TABLE inventory_reservations
    DROP INDEX uq_inventory_reservation_scope;

-- Bước 4: Drop cột cũ
ALTER TABLE inventory_reservations
    DROP COLUMN inventory_item_id;

-- Bước 5: Thêm unique constraint mới với 2 cột mới
ALTER TABLE inventory_reservations
    ADD CONSTRAINT uq_inventory_reservation_scope
        UNIQUE (order_id, product_variant_id,
                inventory_item_product_id, inventory_item_ingredient_id,
                location_id, reservation_type, processing_attempt);

-- Bước 6: Thêm indexes
ALTER TABLE inventory_reservations
    ADD INDEX idx_reservations_product_item (inventory_item_product_id),
    ADD INDEX idx_reservations_ingredient_item (inventory_item_ingredient_id);
