ALTER TABLE orders
    ADD COLUMN inventory_compensation_pending BOOLEAN NOT NULL DEFAULT FALSE;
