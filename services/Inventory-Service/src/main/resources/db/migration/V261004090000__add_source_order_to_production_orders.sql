-- Lệnh sản xuất theo đơn (bán trước – trừ sau): gắn lệnh với đơn hàng đang chờ hàng
-- để không tạo trùng và để các đơn chờ khác không tính trùng năng lực của lệnh này.
ALTER TABLE production_orders
    ADD COLUMN source_order_id BINARY(16) NULL,
    ADD COLUMN source_order_code VARCHAR(50) NULL;

CREATE INDEX idx_production_orders_source_order ON production_orders (source_order_id);
