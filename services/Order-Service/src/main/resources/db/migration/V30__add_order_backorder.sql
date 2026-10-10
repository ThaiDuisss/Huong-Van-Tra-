-- Đơn chờ hàng: đã thu tiền (đủ hoặc đặt cọc) nhưng chưa có hàng giao.
ALTER TABLE orders
    ADD COLUMN backorder BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN expected_available_at DATETIME(6) NULL,
    ADD COLUMN deposit_amount DECIMAL(14, 2) NOT NULL DEFAULT 0;

CREATE INDEX idx_orders_backorder_waiting ON orders (backorder, inventory_processing_status);
