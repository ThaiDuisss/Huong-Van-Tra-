ALTER TABLE customers
    ADD COLUMN last_purchase_date DATETIME       NULL AFTER credit_limit,
    ADD COLUMN total_spent        DECIMAL(15, 2) NOT NULL DEFAULT 0 AFTER last_purchase_date;