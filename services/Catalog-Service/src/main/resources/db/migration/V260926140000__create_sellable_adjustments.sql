CREATE TABLE sellable_adjustments (
    id BINARY(16) NOT NULL PRIMARY KEY,
    variant_id BINARY(16) NOT NULL,
    operation_key VARCHAR(180) NOT NULL,
    quantity DECIMAL(18, 2) NOT NULL,
    old_quantity DECIMAL(18, 2) NOT NULL,
    new_quantity DECIMAL(18, 2) NOT NULL,
    threshold_crossed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_sellable_adjustment_operation UNIQUE (variant_id, operation_key)
);
