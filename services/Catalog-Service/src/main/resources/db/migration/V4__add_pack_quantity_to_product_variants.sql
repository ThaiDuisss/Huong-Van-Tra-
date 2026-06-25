-- Migration V4: Add pack_quantity column to product_variants table
ALTER TABLE product_variants ADD COLUMN pack_quantity INT NOT NULL DEFAULT 1;
