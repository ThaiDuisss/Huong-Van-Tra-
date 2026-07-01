ALTER TABLE customers
    ADD COLUMN total_order_count INT UNSIGNED NOT NULL DEFAULT 0 AFTER total_spent;

ALTER TABLE customer_purchase_transactions
    ADD COLUMN reference_id BINARY(16) NULL AFTER order_id;

ALTER TABLE customer_purchase_transactions
    DROP INDEX uk_customer_purchase_transactions_event;

ALTER TABLE customer_purchase_transactions
    DROP COLUMN source_event_id;

ALTER TABLE customer_purchase_transactions
    ADD CONSTRAINT uk_cpt_transaction_reference UNIQUE (transaction_type, reference_id);

ALTER TABLE customer_tier_assignments
    ADD COLUMN active_customer_id BINARY(16)
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'ACTIVE' AND is_deleted = FALSE THEN customer_id
                ELSE NULL
            END
        ) STORED;

ALTER TABLE customer_tier_assignments
    ADD CONSTRAINT uk_cta_one_active_customer UNIQUE (active_customer_id);
