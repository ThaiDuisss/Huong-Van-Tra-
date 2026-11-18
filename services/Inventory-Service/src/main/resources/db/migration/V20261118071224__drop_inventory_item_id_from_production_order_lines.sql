-- ====================================================================
-- Migration V20261118071224: Drop cột inventory_item_id dư thừa khỏi production_order_lines
--
-- Lý do: Entity ProductionOrderLine dùng cột ingredient_id (FK → inventory_items.id)
-- để lưu nguyên liệu. Cột inventory_item_id (NOT NULL, không có default) được tạo
-- từ migration V260913120000 nhưng không còn được sử dụng sau V261005100000.
-- Hibernate chỉ insert ingredient_id → DB lỗi vì inventory_item_id NOT NULL.
-- ====================================================================

-- Drop index trước khi drop column
ALTER TABLE production_order_lines
    DROP INDEX idx_prod_order_lines_item;

-- Drop cột dư thừa
ALTER TABLE production_order_lines
    DROP COLUMN inventory_item_id;
