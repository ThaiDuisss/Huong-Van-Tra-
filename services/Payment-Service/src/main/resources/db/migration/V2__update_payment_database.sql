ALTER TABLE payments
    ADD COLUMN order_code VARCHAR(50) NULL AFTER order_id,
    ADD COLUMN customer_id BINARY(16) NULL AFTER order_code,
    ADD COLUMN failure_reason TEXT NULL AFTER cancelled_at,
    ADD COLUMN review_reason TEXT NULL AFTER failure_reason,
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER updated_by;

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_method_provider ON payments(method, provider);

ALTER TABLE payment_transactions
    ADD COLUMN review_reason TEXT NULL AFTER raw_payload;

CREATE INDEX idx_payment_transactions_attempt_id ON payment_transactions(attempt_id);
CREATE INDEX idx_payment_transactions_paid_at ON payment_transactions(paid_at);

CREATE INDEX idx_payment_attempts_provider_ref ON payment_attempts(provider_reference);