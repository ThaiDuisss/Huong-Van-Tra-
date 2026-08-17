ALTER TABLE order_discounts
    ADD COLUMN usage_id BINARY(16) NULL;

CREATE INDEX idx_order_discounts_usage_id
    ON order_discounts (usage_id);
