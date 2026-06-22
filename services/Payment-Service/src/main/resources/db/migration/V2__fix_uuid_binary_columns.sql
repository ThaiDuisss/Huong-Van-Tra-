ALTER TABLE payment_transactions DROP FOREIGN KEY fk_payment_transactions_payment;
ALTER TABLE refunds DROP FOREIGN KEY fk_refunds_payment;
ALTER TABLE payment_links DROP FOREIGN KEY fk_payment_links_payment;

ALTER TABLE payments DROP PRIMARY KEY;
ALTER TABLE payments
    ADD COLUMN id_binary BINARY(16) NULL,
    ADD COLUMN order_id_binary BINARY(16) NULL;
UPDATE payments
SET id_binary = UUID_TO_BIN(id),
    order_id_binary = UUID_TO_BIN(order_id);
ALTER TABLE payments
    DROP COLUMN id,
    DROP COLUMN order_id;
ALTER TABLE payments
    CHANGE COLUMN id_binary id BINARY(16) NOT NULL,
    CHANGE COLUMN order_id_binary order_id BINARY(16) NOT NULL,
    MODIFY COLUMN created_by VARCHAR(255) NULL,
    MODIFY COLUMN updated_by VARCHAR(255) NULL;
ALTER TABLE payments ADD PRIMARY KEY (id);
CREATE INDEX idx_payments_order_id ON payments (order_id);

ALTER TABLE payment_transactions DROP PRIMARY KEY;
ALTER TABLE payment_transactions
    ADD COLUMN id_binary BINARY(16) NULL,
    ADD COLUMN payment_id_binary BINARY(16) NULL;
UPDATE payment_transactions
SET id_binary = UUID_TO_BIN(id),
    payment_id_binary = UUID_TO_BIN(payment_id);
ALTER TABLE payment_transactions
    DROP COLUMN id,
    DROP COLUMN payment_id;
ALTER TABLE payment_transactions
    CHANGE COLUMN id_binary id BINARY(16) NOT NULL,
    CHANGE COLUMN payment_id_binary payment_id BINARY(16) NOT NULL,
    MODIFY COLUMN created_by VARCHAR(255) NULL,
    MODIFY COLUMN updated_by VARCHAR(255) NULL;
ALTER TABLE payment_transactions ADD PRIMARY KEY (id);
CREATE INDEX idx_payment_transactions_payment_id ON payment_transactions (payment_id);

ALTER TABLE refunds DROP PRIMARY KEY;
ALTER TABLE refunds
    ADD COLUMN id_binary BINARY(16) NULL,
    ADD COLUMN payment_id_binary BINARY(16) NULL,
    ADD COLUMN return_request_id_binary BINARY(16) NULL;
UPDATE refunds
SET id_binary = UUID_TO_BIN(id),
    payment_id_binary = UUID_TO_BIN(payment_id),
    return_request_id_binary = IF(return_request_id IS NULL OR return_request_id = '', NULL, UUID_TO_BIN(return_request_id));
ALTER TABLE refunds
    DROP COLUMN id,
    DROP COLUMN payment_id,
    DROP COLUMN return_request_id;
ALTER TABLE refunds
    CHANGE COLUMN id_binary id BINARY(16) NOT NULL,
    CHANGE COLUMN payment_id_binary payment_id BINARY(16) NOT NULL,
    CHANGE COLUMN return_request_id_binary return_request_id BINARY(16) NULL,
    MODIFY COLUMN created_by VARCHAR(255) NULL,
    MODIFY COLUMN updated_by VARCHAR(255) NULL;
ALTER TABLE refunds ADD PRIMARY KEY (id);
CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);
CREATE INDEX idx_refunds_return_request_id ON refunds (return_request_id);

ALTER TABLE payment_links DROP PRIMARY KEY;
ALTER TABLE payment_links
    ADD COLUMN id_binary BINARY(16) NULL,
    ADD COLUMN payment_id_binary BINARY(16) NULL;
UPDATE payment_links
SET id_binary = UUID_TO_BIN(id),
    payment_id_binary = UUID_TO_BIN(payment_id);
ALTER TABLE payment_links
    DROP COLUMN id,
    DROP COLUMN payment_id;
ALTER TABLE payment_links
    CHANGE COLUMN id_binary id BINARY(16) NOT NULL,
    CHANGE COLUMN payment_id_binary payment_id BINARY(16) NOT NULL,
    MODIFY COLUMN created_by VARCHAR(255) NULL,
    MODIFY COLUMN updated_by VARCHAR(255) NULL;
ALTER TABLE payment_links ADD PRIMARY KEY (id);
CREATE INDEX idx_payment_links_payment_id ON payment_links (payment_id);

ALTER TABLE payment_webhooks DROP PRIMARY KEY;
ALTER TABLE payment_webhooks
    ADD COLUMN id_binary BINARY(16) NULL;
UPDATE payment_webhooks
SET id_binary = UUID_TO_BIN(id);
ALTER TABLE payment_webhooks
    DROP COLUMN id;
ALTER TABLE payment_webhooks
    CHANGE COLUMN id_binary id BINARY(16) NOT NULL,
    MODIFY COLUMN created_by VARCHAR(255) NULL,
    MODIFY COLUMN updated_by VARCHAR(255) NULL;
ALTER TABLE payment_webhooks ADD PRIMARY KEY (id);

ALTER TABLE payment_transactions
    ADD CONSTRAINT fk_payment_transactions_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id)
            ON DELETE CASCADE;
ALTER TABLE refunds
    ADD CONSTRAINT fk_refunds_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id)
            ON DELETE CASCADE;
ALTER TABLE payment_links
    ADD CONSTRAINT fk_payment_links_payment
        FOREIGN KEY (payment_id) REFERENCES payments(id)
            ON DELETE CASCADE;
