-- ====================================================================
-- Nguyên liệu không quản lý tồn theo kho sản xuất — lúc duyệt (approve) lệnh sản xuất, hệ thống lấy
-- nguyên liệu theo FEFO bất kể batch đang nằm ở kho nào (khác với "Kho / vị trí sản xuất" của lệnh,
-- vốn chỉ là nơi nhập thành phẩm sau khi hoàn tất). Thêm cột location_id để lưu lại đúng kho đã xuất
-- nguyên liệu cho từng allocation, phục vụ release()/cancel() hoàn tác/tiêu thụ đúng bản ghi tồn kho.
-- ====================================================================

ALTER TABLE production_order_material_allocations
    ADD COLUMN location_id BINARY(16) NULL AFTER inventory_batch_id;
