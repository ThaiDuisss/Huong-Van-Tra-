-- ============================================================
-- Consolidate Migration V1: Khởi tạo cơ sở dữ liệu Catalog-Service
-- Định dạng chuẩn BINARY(16) cho khóa chính/phụ và đầy đủ các cột mới nhất
-- Target Database: MySQL 8
-- ============================================================

CREATE TABLE IF NOT EXISTS categories (
    id BINARY(16) PRIMARY KEY,
    parent_id BINARY(16) NULL,
    category_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    default_reorder_point INT NOT NULL DEFAULT 10,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL,
    INDEX idx_categories_parent (parent_id)
);

CREATE TABLE IF NOT EXISTS products (
    id BINARY(16) PRIMARY KEY,
    category_id BINARY(16) NOT NULL,
    product_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    image_url VARCHAR(500) NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    INDEX idx_products_category (category_id)
);

CREATE TABLE IF NOT EXISTS units (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    precision_scale INT NOT NULL DEFAULT 0,
    `type` VARCHAR(20) NOT NULL DEFAULT 'BASE',
    base_unit_id BINARY(16) NULL,
    conversion_rate DECIMAL(18, 4) NOT NULL DEFAULT 1.0000,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_units_base_unit FOREIGN KEY (base_unit_id) REFERENCES units(id) ON DELETE SET NULL,
    INDEX idx_units_base_unit (base_unit_id)
);

CREATE TABLE IF NOT EXISTS product_variants (
    id BINARY(16) PRIMARY KEY,
    product_id BINARY(16) NOT NULL,
    sku VARCHAR(100) NOT NULL UNIQUE,
    variant_name VARCHAR(255) NOT NULL,
    selling_price DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    cost_price DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    unit_id BINARY(16) NOT NULL,
    inventory_policy VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT fk_product_variants_unit FOREIGN KEY (unit_id) REFERENCES units(id),
    INDEX idx_product_variants_product (product_id),
    INDEX idx_product_variants_unit (unit_id)
);

CREATE TABLE IF NOT EXISTS barcodes (
    id BINARY(16) PRIMARY KEY,
    product_variant_id BINARY(16) NOT NULL,
    barcode_value VARCHAR(255) NOT NULL UNIQUE,
    barcode_type VARCHAR(50) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_barcodes_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    INDEX idx_barcodes_variant (product_variant_id)
);

CREATE TABLE IF NOT EXISTS ingredients (
    id BINARY(16) PRIMARY KEY,
    ingredient_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS boms (
    id BINARY(16) PRIMARY KEY,
    product_variant_id BINARY(16) NOT NULL,
    name VARCHAR(255) NULL,
    bom_code VARCHAR(100) NULL UNIQUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(1000) NULL,
    version INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'INACTIVE',
    effective_from DATETIME NULL,
    effective_to DATETIME NULL,

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version_audit BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_boms_variant_version (product_variant_id, version),
    INDEX idx_boms_variant (product_variant_id)
);

CREATE TABLE IF NOT EXISTS bom_items (
    id BINARY(16) PRIMARY KEY,
    bom_id BINARY(16) NOT NULL,
    ingredient_id BINARY(16) NOT NULL,
    quantity DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    unit_id BINARY(16) NOT NULL,
    waste_rate DECIMAL(5, 2) NOT NULL DEFAULT 0.00,

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
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

CREATE TABLE IF NOT EXISTS price_lists (
    id BINARY(16) PRIMARY KEY,
    branch_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    effective_from DATETIME NULL,
    effective_to DATETIME NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS price_list_items (
    id BINARY(16) PRIMARY KEY,
    price_list_id BINARY(16) NOT NULL,
    product_variant_id BINARY(16) NOT NULL,
    selling_price DECIMAL(18, 2) NOT NULL DEFAULT 0.00,

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_price_list_items_list_variant (price_list_id, product_variant_id),
    CONSTRAINT fk_price_list_items_list FOREIGN KEY (price_list_id) REFERENCES price_lists(id) ON DELETE CASCADE,
    CONSTRAINT fk_price_list_items_variant FOREIGN KEY (product_variant_id) REFERENCES product_variants(id) ON DELETE CASCADE,
    INDEX idx_price_list_items_list (price_list_id),
    INDEX idx_price_list_items_variant (product_variant_id)
);

CREATE TABLE IF NOT EXISTS business_sequences (
    sequence_key VARCHAR(50) PRIMARY KEY,
    current_value BIGINT NOT NULL
);

-- Khởi tạo ban đầu cho các mã tự sinh
INSERT INTO business_sequences (sequence_key, current_value) VALUES
('CATEGORY', 0),
('PRODUCT', 0),
('SKU', 0),
('BOM', 0)
ON DUPLICATE KEY UPDATE current_value = current_value;
