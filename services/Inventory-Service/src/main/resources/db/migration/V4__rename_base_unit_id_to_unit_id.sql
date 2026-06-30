-- ============================================================
-- Migration V4: Rename base_unit_id to unit_id in inventory_items
-- Inventory-Service
-- ============================================================
-- Đổi tên cột base_unit_id thành unit_id cho rõ ý nghĩa lưu kho
ALTER TABLE inventory_items
    RENAME COLUMN base_unit_id TO unit_id;