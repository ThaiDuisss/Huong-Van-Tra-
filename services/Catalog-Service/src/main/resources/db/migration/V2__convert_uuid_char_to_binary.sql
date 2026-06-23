-- Flyway migration to convert UUID columns from CHAR(36) to BINARY(16)
-- Target Database: MySQL 8
-- Safe for existing data: uses UUID_TO_BIN()

-- =========================================================================
-- STEP 1: Drop all foreign key constraints referencing the affected tables
-- =========================================================================

ALTER TABLE categories DROP FOREIGN KEY fk_categories_parent;
ALTER TABLE products DROP FOREIGN KEY fk_products_category;
ALTER TABLE product_variants DROP FOREIGN KEY fk_product_variants_product;
ALTER TABLE product_variants DROP FOREIGN KEY fk_product_variants_unit;
ALTER TABLE barcodes DROP FOREIGN KEY fk_barcodes_variant;
ALTER TABLE ingredients DROP FOREIGN KEY fk_ingredients_unit;
ALTER TABLE bom_items DROP FOREIGN KEY fk_bom_items_bom;
ALTER TABLE bom_items DROP FOREIGN KEY fk_bom_items_ingredient;
ALTER TABLE bom_items DROP FOREIGN KEY fk_bom_items_unit;
ALTER TABLE price_list_items DROP FOREIGN KEY fk_price_list_items_list;
ALTER TABLE price_list_items DROP FOREIGN KEY fk_price_list_items_variant;

-- =========================================================================
-- STEP 2: Convert Primary Key and Foreign Key columns in each table
-- =========================================================================

-- -------------------------------------------------------------------------
-- Table: categories
-- -------------------------------------------------------------------------
ALTER TABLE categories DROP PRIMARY KEY;
ALTER TABLE categories ADD COLUMN temp_id BINARY(16) NULL;
ALTER TABLE categories ADD COLUMN temp_parent_id BINARY(16) NULL;

UPDATE categories SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;
UPDATE categories SET temp_parent_id = UUID_TO_BIN(parent_id) WHERE parent_id IS NOT NULL;

ALTER TABLE categories DROP COLUMN id;
ALTER TABLE categories DROP COLUMN parent_id;

ALTER TABLE categories RENAME COLUMN temp_id TO id;
ALTER TABLE categories RENAME COLUMN temp_parent_id TO parent_id;

ALTER TABLE categories MODIFY COLUMN id BINARY(16) NOT NULL;
ALTER TABLE categories MODIFY COLUMN parent_id BINARY(16) NULL;

ALTER TABLE categories ADD PRIMARY KEY (id);
CREATE INDEX idx_categories_parent ON categories (parent_id);

-- -------------------------------------------------------------------------
-- Table: products
-- -------------------------------------------------------------------------
ALTER TABLE products DROP PRIMARY KEY;
ALTER TABLE products ADD COLUMN temp_id BINARY(16) NULL;
ALTER TABLE products ADD COLUMN temp_category_id BINARY(16) NULL;

UPDATE products SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;
UPDATE products SET temp_category_id = UUID_TO_BIN(category_id) WHERE category_id IS NOT NULL;

ALTER TABLE products DROP COLUMN id;
ALTER TABLE products DROP COLUMN category_id;

ALTER TABLE products RENAME COLUMN temp_id TO id;
ALTER TABLE products RENAME COLUMN temp_category_id TO category_id;

ALTER TABLE products MODIFY COLUMN id BINARY(16) NOT NULL;
ALTER TABLE products MODIFY COLUMN category_id BINARY(16) NOT NULL;

ALTER TABLE products ADD PRIMARY KEY (id);
CREATE INDEX idx_products_category ON products (category_id);

-- -------------------------------------------------------------------------
-- Table: units
-- -------------------------------------------------------------------------
ALTER TABLE units DROP PRIMARY KEY;
ALTER TABLE units ADD COLUMN temp_id BINARY(16) NULL;

UPDATE units SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;

ALTER TABLE units DROP COLUMN id;

ALTER TABLE units RENAME COLUMN temp_id TO id;

ALTER TABLE units MODIFY COLUMN id BINARY(16) NOT NULL;

ALTER TABLE units ADD PRIMARY KEY (id);

-- -------------------------------------------------------------------------
-- Table: product_variants
-- -------------------------------------------------------------------------
ALTER TABLE product_variants DROP PRIMARY KEY;
ALTER TABLE product_variants ADD COLUMN temp_id BINARY(16) NULL;
ALTER TABLE product_variants ADD COLUMN temp_product_id BINARY(16) NULL;
ALTER TABLE product_variants ADD COLUMN temp_base_unit_id BINARY(16) NULL;

UPDATE product_variants SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;
UPDATE product_variants SET temp_product_id = UUID_TO_BIN(product_id) WHERE product_id IS NOT NULL;
UPDATE product_variants SET temp_base_unit_id = UUID_TO_BIN(base_unit_id) WHERE base_unit_id IS NOT NULL;

ALTER TABLE product_variants DROP COLUMN id;
ALTER TABLE product_variants DROP COLUMN product_id;
ALTER TABLE product_variants DROP COLUMN base_unit_id;

ALTER TABLE product_variants RENAME COLUMN temp_id TO id;
ALTER TABLE product_variants RENAME COLUMN temp_product_id TO product_id;
ALTER TABLE product_variants RENAME COLUMN temp_base_unit_id TO base_unit_id;

ALTER TABLE product_variants MODIFY COLUMN id BINARY(16) NOT NULL;
ALTER TABLE product_variants MODIFY COLUMN product_id BINARY(16) NOT NULL;
ALTER TABLE product_variants MODIFY COLUMN base_unit_id BINARY(16) NOT NULL;

ALTER TABLE product_variants ADD PRIMARY KEY (id);
CREATE INDEX idx_product_variants_product ON product_variants (product_id);
CREATE INDEX idx_product_variants_unit ON product_variants (base_unit_id);

-- -------------------------------------------------------------------------
-- Table: barcodes
-- -------------------------------------------------------------------------
ALTER TABLE barcodes DROP PRIMARY KEY;
ALTER TABLE barcodes ADD COLUMN temp_id BINARY(16) NULL;
ALTER TABLE barcodes ADD COLUMN temp_product_variant_id BINARY(16) NULL;

UPDATE barcodes SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;
UPDATE barcodes SET temp_product_variant_id = UUID_TO_BIN(product_variant_id) WHERE product_variant_id IS NOT NULL;

ALTER TABLE barcodes DROP COLUMN id;
ALTER TABLE barcodes DROP COLUMN product_variant_id;

ALTER TABLE barcodes RENAME COLUMN temp_id TO id;
ALTER TABLE barcodes RENAME COLUMN temp_product_variant_id TO product_variant_id;

ALTER TABLE barcodes MODIFY COLUMN id BINARY(16) NOT NULL;
ALTER TABLE barcodes MODIFY COLUMN product_variant_id BINARY(16) NOT NULL;

ALTER TABLE barcodes ADD PRIMARY KEY (id);
CREATE INDEX idx_barcodes_variant ON barcodes (product_variant_id);

-- -------------------------------------------------------------------------
-- Table: ingredients
-- -------------------------------------------------------------------------
ALTER TABLE ingredients DROP PRIMARY KEY;
ALTER TABLE ingredients ADD COLUMN temp_id BINARY(16) NULL;
ALTER TABLE ingredients ADD COLUMN temp_base_unit_id BINARY(16) NULL;

UPDATE ingredients SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;
UPDATE ingredients SET temp_base_unit_id = UUID_TO_BIN(base_unit_id) WHERE base_unit_id IS NOT NULL;

ALTER TABLE ingredients DROP COLUMN id;
ALTER TABLE ingredients DROP COLUMN base_unit_id;

ALTER TABLE ingredients RENAME COLUMN temp_id TO id;
ALTER TABLE ingredients RENAME COLUMN temp_base_unit_id TO base_unit_id;

ALTER TABLE ingredients MODIFY COLUMN id BINARY(16) NOT NULL;
ALTER TABLE ingredients MODIFY COLUMN base_unit_id BINARY(16) NOT NULL;

ALTER TABLE ingredients ADD PRIMARY KEY (id);
CREATE INDEX idx_ingredients_unit ON ingredients (base_unit_id);

-- -------------------------------------------------------------------------
-- Table: boms
-- -------------------------------------------------------------------------
ALTER TABLE boms DROP PRIMARY KEY;
ALTER TABLE boms ADD COLUMN temp_id BINARY(16) NULL;

UPDATE boms SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;

ALTER TABLE boms DROP COLUMN id;

ALTER TABLE boms RENAME COLUMN temp_id TO id;

ALTER TABLE boms MODIFY COLUMN id BINARY(16) NOT NULL;

ALTER TABLE boms ADD PRIMARY KEY (id);

-- -------------------------------------------------------------------------
-- Table: bom_items
-- -------------------------------------------------------------------------
ALTER TABLE bom_items DROP PRIMARY KEY;
ALTER TABLE bom_items ADD COLUMN temp_id BINARY(16) NULL;
ALTER TABLE bom_items ADD COLUMN temp_bom_id BINARY(16) NULL;
ALTER TABLE bom_items ADD COLUMN temp_ingredient_id BINARY(16) NULL;
ALTER TABLE bom_items ADD COLUMN temp_unit_id BINARY(16) NULL;

UPDATE bom_items SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;
UPDATE bom_items SET temp_bom_id = UUID_TO_BIN(bom_id) WHERE bom_id IS NOT NULL;
UPDATE bom_items SET temp_ingredient_id = UUID_TO_BIN(ingredient_id) WHERE ingredient_id IS NOT NULL;
UPDATE bom_items SET temp_unit_id = UUID_TO_BIN(unit_id) WHERE unit_id IS NOT NULL;

ALTER TABLE bom_items DROP COLUMN id;
ALTER TABLE bom_items DROP COLUMN bom_id;
ALTER TABLE bom_items DROP COLUMN ingredient_id;
ALTER TABLE bom_items DROP COLUMN unit_id;

ALTER TABLE bom_items RENAME COLUMN temp_id TO id;
ALTER TABLE bom_items RENAME COLUMN temp_bom_id TO bom_id;
ALTER TABLE bom_items RENAME COLUMN temp_ingredient_id TO ingredient_id;
ALTER TABLE bom_items RENAME COLUMN temp_unit_id TO unit_id;

ALTER TABLE bom_items MODIFY COLUMN id BINARY(16) NOT NULL;
ALTER TABLE bom_items MODIFY COLUMN bom_id BINARY(16) NOT NULL;
ALTER TABLE bom_items MODIFY COLUMN ingredient_id BINARY(16) NOT NULL;
ALTER TABLE bom_items MODIFY COLUMN unit_id BINARY(16) NOT NULL;

ALTER TABLE bom_items ADD PRIMARY KEY (id);
CREATE INDEX idx_bom_items_bom ON bom_items (bom_id);
CREATE INDEX idx_bom_items_ingredient ON bom_items (ingredient_id);
CREATE INDEX idx_bom_items_unit ON bom_items (unit_id);

-- -------------------------------------------------------------------------
-- Table: price_lists
-- -------------------------------------------------------------------------
ALTER TABLE price_lists DROP PRIMARY KEY;
ALTER TABLE price_lists ADD COLUMN temp_id BINARY(16) NULL;

UPDATE price_lists SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;

ALTER TABLE price_lists DROP COLUMN id;

ALTER TABLE price_lists RENAME COLUMN temp_id TO id;

ALTER TABLE price_lists MODIFY COLUMN id BINARY(16) NOT NULL;

ALTER TABLE price_lists ADD PRIMARY KEY (id);

-- -------------------------------------------------------------------------
-- Table: price_list_items
-- -------------------------------------------------------------------------
ALTER TABLE price_list_items DROP PRIMARY KEY;
ALTER TABLE price_list_items ADD COLUMN temp_id BINARY(16) NULL;
ALTER TABLE price_list_items ADD COLUMN temp_price_list_id BINARY(16) NULL;
ALTER TABLE price_list_items ADD COLUMN temp_product_variant_id BINARY(16) NULL;

UPDATE price_list_items SET temp_id = UUID_TO_BIN(id) WHERE id IS NOT NULL;
UPDATE price_list_items SET temp_price_list_id = UUID_TO_BIN(price_list_id) WHERE price_list_id IS NOT NULL;
UPDATE price_list_items SET temp_product_variant_id = UUID_TO_BIN(product_variant_id) WHERE product_variant_id IS NOT NULL;

ALTER TABLE price_list_items DROP COLUMN id;
ALTER TABLE price_list_items DROP COLUMN price_list_id;
ALTER TABLE price_list_items DROP COLUMN product_variant_id;

ALTER TABLE price_list_items RENAME COLUMN temp_id TO id;
ALTER TABLE price_list_items RENAME COLUMN temp_price_list_id TO price_list_id;
ALTER TABLE price_list_items RENAME COLUMN temp_product_variant_id TO product_variant_id;

ALTER TABLE price_list_items MODIFY COLUMN id BINARY(16) NOT NULL;
ALTER TABLE price_list_items MODIFY COLUMN price_list_id BINARY(16) NOT NULL;
ALTER TABLE price_list_items MODIFY COLUMN product_variant_id BINARY(16) NOT NULL;

ALTER TABLE price_list_items ADD PRIMARY KEY (id);
CREATE UNIQUE INDEX uq_price_list_items_list_variant ON price_list_items (price_list_id, product_variant_id);
CREATE INDEX idx_price_list_items_list ON price_list_items (price_list_id);
CREATE INDEX idx_price_list_items_variant ON price_list_items (product_variant_id);


-- =========================================================================
-- STEP 3: Recreate all foreign key constraints with BINARY(16) types
-- =========================================================================

ALTER TABLE categories ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL;
ALTER TABLE products ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id);
ALTER TABLE product_variants ADD CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE product_variants ADD CONSTRAINT fk_product_variants_unit FOREIGN KEY (base_unit_id) REFERENCES units(id);
ALTER TABLE barcodes ADD CONSTRAINT fk_barcodes_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE CASCADE;
ALTER TABLE ingredients ADD CONSTRAINT fk_ingredients_unit FOREIGN KEY (base_unit_id) REFERENCES units(id);
ALTER TABLE bom_items ADD CONSTRAINT fk_bom_items_bom FOREIGN KEY (bom_id) REFERENCES boms(id) ON DELETE CASCADE;
ALTER TABLE bom_items ADD CONSTRAINT fk_bom_items_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id);
ALTER TABLE bom_items ADD CONSTRAINT fk_bom_items_unit FOREIGN KEY (unit_id) REFERENCES units(id);
ALTER TABLE price_list_items ADD CONSTRAINT fk_price_list_items_list FOREIGN KEY (price_list_id) REFERENCES price_lists(id) ON DELETE CASCADE;
ALTER TABLE price_list_items ADD CONSTRAINT fk_price_list_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE CASCADE;
