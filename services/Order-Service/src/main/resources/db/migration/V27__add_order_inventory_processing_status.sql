ALTER TABLE orders
    ADD COLUMN inventory_processing_status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN inventory_processed_at DATETIME(6) NULL,
    ADD COLUMN inventory_failure_reason VARCHAR(1000) NULL,
    ADD COLUMN sellable_compensated_at DATETIME(6) NULL;

UPDATE orders
SET inventory_processing_status = 'CONSUMED'
WHERE payment_status = 'PAID';
