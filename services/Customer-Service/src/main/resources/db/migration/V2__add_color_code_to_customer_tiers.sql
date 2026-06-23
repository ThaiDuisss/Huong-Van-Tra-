ALTER TABLE customer_tiers
    ADD COLUMN color_code VARCHAR(7) NOT NULL DEFAULT '#6B7280'
        AFTER name;