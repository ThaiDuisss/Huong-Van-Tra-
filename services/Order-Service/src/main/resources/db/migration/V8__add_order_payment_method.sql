ALTER TABLE orders
    ADD COLUMN payment_method VARCHAR(50) NULL AFTER payment_status,
    ADD COLUMN payment_code VARCHAR(100) NULL AFTER payment_method;
