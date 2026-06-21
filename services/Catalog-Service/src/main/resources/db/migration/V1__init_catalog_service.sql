CREATE TABLE categories (
    id CHAR(36) PRIMARY KEY,
    parent_id CHAR(36) NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL,
    INDEX idx_categories_parent (parent_id)
);

CREATE TABLE products (
    id CHAR(36) PRIMARY KEY,
    category_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    image_url VARCHAR(500) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    INDEX idx_products_category (category_id)
);

CREATE TABLE units (
    id CHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    precision_scale INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE product_variants (
    id CHAR(36) PRIMARY KEY,
    product_id CHAR(36) NOT NULL,
    sku VARCHAR(100) NOT NULL UNIQUE,
    variant_name VARCHAR(255) NOT NULL,
    selling_price DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    cost_price DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    base_unit_id CHAR(36) NOT NULL,
    inventory_policy VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_variants_unit FOREIGN KEY (base_unit_id) REFERENCES units(id),
    INDEX idx_product_variants_product (product_id),
    INDEX idx_product_variants_unit (base_unit_id)
);

CREATE TABLE barcodes (
    id CHAR(36) PRIMARY KEY,
    product_variant_id CHAR(36) NOT NULL,
    barcode_value VARCHAR(255) NOT NULL UNIQUE,
    barcode_type VARCHAR(50) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_barcodes_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    INDEX idx_barcodes_variant (product_variant_id)
);

CREATE TABLE ingredients (
    id CHAR(36) PRIMARY KEY,
    ingredient_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    base_unit_id CHAR(36) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_ingredients_unit FOREIGN KEY (base_unit_id) REFERENCES units(id),
    INDEX idx_ingredients_unit (base_unit_id)
);

CREATE TABLE boms (
    id CHAR(36) PRIMARY KEY,
    product_variant_id CHAR(36) NOT NULL,
    version INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'INACTIVE',
    effective_from DATETIME NULL,
    effective_to DATETIME NULL,

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version_audit BIGINT NULL, -- renamed from 'version' to avoid conflicts if needed, but version in BaseEntity is mapped to 'version'. So standard 'version' column is fine.
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_boms_variant_version (product_variant_id, version),
    INDEX idx_boms_variant (product_variant_id)
);

CREATE TABLE bom_items (
    id CHAR(36) PRIMARY KEY,
    bom_id CHAR(36) NOT NULL,
    ingredient_id CHAR(36) NOT NULL,
    quantity DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    unit_id CHAR(36) NOT NULL,
    waste_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00,

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_bom_items_bom FOREIGN KEY (bom_id) REFERENCES boms(id) ON DELETE CASCADE,
    CONSTRAINT fk_bom_items_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id),
    CONSTRAINT fk_bom_items_unit FOREIGN KEY (unit_id) REFERENCES units(id),
    INDEX idx_bom_items_bom (bom_id),
    INDEX idx_bom_items_ingredient (ingredient_id),
    INDEX idx_bom_items_unit (unit_id)
);

CREATE TABLE price_lists (
    id CHAR(36) PRIMARY KEY,
    branch_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    effective_from DATETIME NULL,
    effective_to DATETIME NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE price_list_items (
    id CHAR(36) PRIMARY KEY,
    price_list_id CHAR(36) NOT NULL,
    product_variant_id CHAR(36) NOT NULL,
    selling_price DECIMAL(18, 2) NOT NULL DEFAULT 0.00,

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_price_list_items_list_variant (price_list_id, product_variant_id),
    CONSTRAINT fk_price_list_items_list FOREIGN KEY (price_list_id) REFERENCES price_lists(id) ON DELETE CASCADE,
    CONSTRAINT fk_price_list_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    INDEX idx_price_list_items_list (price_list_id),
    INDEX idx_price_list_items_variant (product_variant_id)
);
