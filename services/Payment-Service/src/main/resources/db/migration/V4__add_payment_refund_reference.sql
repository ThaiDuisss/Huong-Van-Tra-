ALTER TABLE payment_transactions
    ADD COLUMN refund_reference VARCHAR(100) NULL AFTER reference_code;

CREATE UNIQUE INDEX uk_payment_transactions_refund_reference
    ON payment_transactions(refund_reference);
