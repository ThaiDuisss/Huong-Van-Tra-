-- ============================================================
-- Migration V12: Thêm unit_id và reorder_point vào bảng ingredients
-- ============================================================

ALTER TABLE ingredients ADD COLUMN unit_id BINARY(16) NULL AFTER name;
ALTER TABLE ingredients ADD COLUMN reorder_point INT NOT NULL DEFAULT 0 AFTER unit_id;
ALTER TABLE ingredients ADD CONSTRAINT fk_ingredients_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE SET NULL;
ALTER TABLE ingredients ADD INDEX idx_ingredients_unit (unit_id);
