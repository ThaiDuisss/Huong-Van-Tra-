-- Single-store customer returns may have no branch. Keep the columns for
-- existing data and reporting while allowing inspection, disposal and stock
-- transactions to retain a null branch when the return has no branch.
ALTER TABLE customer_return_inspections
    MODIFY COLUMN branch_id BINARY(16) NULL;

ALTER TABLE disposal_requests
    MODIFY COLUMN branch_id BINARY(16) NULL;

ALTER TABLE inventory_transactions
    MODIFY COLUMN branch_id BINARY(16) NULL;
