-- Flyway migration to add category_code and product_code
-- Safe for existing data, generates CATXXXXX and PRDXXXXX codes

-- 1. categories
ALTER TABLE categories ADD COLUMN category_code VARCHAR(50) NULL;

UPDATE categories c
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) as rn
    FROM categories
) n ON c.id = n.id
SET c.category_code = CONCAT('CAT', LPAD(n.rn, 5, '0'));

ALTER TABLE categories MODIFY COLUMN category_code VARCHAR(50) NOT NULL;
ALTER TABLE categories ADD CONSTRAINT uq_categories_code UNIQUE (category_code);

-- 2. products
ALTER TABLE products ADD COLUMN product_code VARCHAR(50) NULL;

UPDATE products p
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) as rn
    FROM products
) n ON p.id = n.id
SET p.product_code = CONCAT('PRD', LPAD(n.rn, 5, '0'));

ALTER TABLE products MODIFY COLUMN product_code VARCHAR(50) NOT NULL;
ALTER TABLE products ADD CONSTRAINT uq_products_code UNIQUE (product_code);

-- 3. business_sequences
CREATE TABLE business_sequences (
    sequence_key VARCHAR(50) PRIMARY KEY,
    current_value BIGINT NOT NULL
);

INSERT INTO business_sequences (sequence_key, current_value)
SELECT 'CATEGORY', COUNT(*) FROM categories;

INSERT INTO business_sequences (sequence_key, current_value)
SELECT 'PRODUCT', COUNT(*) FROM products;
