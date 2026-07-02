ALTER TABLE orders
    ADD COLUMN expires_at DATETIME NULL,
    ADD INDEX idx_orders_status_expires_at (status, expires_at);
