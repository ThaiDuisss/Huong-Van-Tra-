-- Migration V5: Add default_reorder_point to categories table
ALTER TABLE categories ADD COLUMN default_reorder_point INT NOT NULL DEFAULT 10;
