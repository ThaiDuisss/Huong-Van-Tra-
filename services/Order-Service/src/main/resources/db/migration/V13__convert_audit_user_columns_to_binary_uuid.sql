ALTER TABLE orders
    ADD COLUMN created_by_binary BINARY(16) NULL,
    ADD COLUMN updated_by_binary BINARY(16) NULL;

UPDATE orders
SET created_by_binary = CASE
        WHEN created_by IS NULL OR created_by = '' THEN NULL
        WHEN created_by NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN NULL
        ELSE UUID_TO_BIN(created_by)
    END,
    updated_by_binary = CASE
        WHEN updated_by IS NULL OR updated_by = '' THEN NULL
        WHEN updated_by NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN NULL
        ELSE UUID_TO_BIN(updated_by)
    END;

ALTER TABLE orders
    DROP COLUMN created_by,
    DROP COLUMN updated_by;

ALTER TABLE orders
    CHANGE COLUMN created_by_binary created_by BINARY(16) NULL,
    CHANGE COLUMN updated_by_binary updated_by BINARY(16) NULL;

ALTER TABLE order_items
    ADD COLUMN created_by_binary BINARY(16) NULL,
    ADD COLUMN updated_by_binary BINARY(16) NULL;

UPDATE order_items
SET created_by_binary = CASE
        WHEN created_by IS NULL OR created_by = '' THEN NULL
        WHEN created_by NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN NULL
        ELSE UUID_TO_BIN(created_by)
    END,
    updated_by_binary = CASE
        WHEN updated_by IS NULL OR updated_by = '' THEN NULL
        WHEN updated_by NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN NULL
        ELSE UUID_TO_BIN(updated_by)
    END;

ALTER TABLE order_items
    DROP COLUMN created_by,
    DROP COLUMN updated_by;

ALTER TABLE order_items
    CHANGE COLUMN created_by_binary created_by BINARY(16) NULL,
    CHANGE COLUMN updated_by_binary updated_by BINARY(16) NULL;

ALTER TABLE order_status_history
    ADD COLUMN created_by_binary BINARY(16) NULL,
    ADD COLUMN updated_by_binary BINARY(16) NULL;

UPDATE order_status_history
SET created_by_binary = CASE
        WHEN created_by IS NULL OR created_by = '' THEN NULL
        WHEN created_by NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN NULL
        ELSE UUID_TO_BIN(created_by)
    END,
    updated_by_binary = CASE
        WHEN updated_by IS NULL OR updated_by = '' THEN NULL
        WHEN updated_by NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN NULL
        ELSE UUID_TO_BIN(updated_by)
    END;

ALTER TABLE order_status_history
    DROP COLUMN created_by,
    DROP COLUMN updated_by;

ALTER TABLE order_status_history
    CHANGE COLUMN created_by_binary created_by BINARY(16) NULL,
    CHANGE COLUMN updated_by_binary updated_by BINARY(16) NULL;

ALTER TABLE order_discounts
    ADD COLUMN created_by_binary BINARY(16) NULL,
    ADD COLUMN updated_by_binary BINARY(16) NULL;

UPDATE order_discounts
SET created_by_binary = CASE
        WHEN created_by IS NULL OR created_by = '' THEN NULL
        WHEN created_by NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN NULL
        ELSE UUID_TO_BIN(created_by)
    END,
    updated_by_binary = CASE
        WHEN updated_by IS NULL OR updated_by = '' THEN NULL
        WHEN updated_by NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN NULL
        ELSE UUID_TO_BIN(updated_by)
    END;

ALTER TABLE order_discounts
    DROP COLUMN created_by,
    DROP COLUMN updated_by;

ALTER TABLE order_discounts
    CHANGE COLUMN created_by_binary created_by BINARY(16) NULL,
    CHANGE COLUMN updated_by_binary updated_by BINARY(16) NULL;
