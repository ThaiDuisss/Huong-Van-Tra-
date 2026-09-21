ALTER TABLE orders
    ADD COLUMN return_status VARCHAR(50) NOT NULL DEFAULT 'NONE' AFTER payment_status;

CREATE INDEX idx_orders_return_status
    ON orders(return_status);
