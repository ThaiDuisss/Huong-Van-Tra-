-- Đơn bán offline từ POS: client sinh client_order_id (UUID) làm khoá idempotency,
-- gửi lại nhiều lần khi đồng bộ vẫn chỉ tạo một đơn. sold_at là giờ bán thật tại quầy.
ALTER TABLE orders
    ADD COLUMN client_order_id BINARY(16) NULL,
    ADD COLUMN sold_at DATETIME(6) NULL,
    ADD COLUMN offline_sale BOOLEAN NOT NULL DEFAULT FALSE,
    ADD UNIQUE KEY uk_orders_client_order_id (client_order_id);
