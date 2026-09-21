-- Recommendation-Service read model
-- Target database: MySQL 8 / recommendation_db
-- Flyway owns this schema. SQLAlchemy models are mappings only.

CREATE TABLE categories (
    id CHAR(36) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    parent_id CHAR(36) NULL,
    status VARCHAR(50) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_categories_category_code UNIQUE (category_code),
    INDEX idx_categories_status_deleted (status, is_deleted),
    INDEX idx_categories_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE products (
    id CHAR(36) NOT NULL,
    category_id CHAR(36) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_products_product_code UNIQUE (product_code),
    INDEX idx_products_category_id (category_id),
    INDEX idx_products_status_deleted (status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_variants (
    id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    variant_name VARCHAR(255) NOT NULL,
    selling_price DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(50) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_product_variants_sku UNIQUE (sku),
    INDEX idx_product_variants_product_id (product_id),
    INDEX idx_product_variants_status_deleted (status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ingredients (
    id CHAR(36) NOT NULL,
    ingredient_code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_ingredients_ingredient_code UNIQUE (ingredient_code),
    INDEX idx_ingredients_status_deleted (status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_ingredients (
    id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    ingredient_id CHAR(36) NOT NULL,
    ingredient_code VARCHAR(100) NOT NULL,
    source_variant_id CHAR(36) NOT NULL,
    bom_version INT NOT NULL,
    is_active_bom BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_product_ingredients_source
        UNIQUE (product_id, ingredient_id, source_variant_id, bom_version),
    INDEX idx_product_ingredients_product_id (product_id),
    INDEX idx_product_ingredients_ingredient_id (ingredient_id),
    INDEX idx_product_ingredients_source_variant_id (source_variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE interaction_events (
    id CHAR(36) NOT NULL,
    source_event_id CHAR(36) NOT NULL,
    order_item_id CHAR(36) NOT NULL,
    return_item_id CHAR(36) NULL,
    customer_id CHAR(36) NULL,
    product_variant_id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    quantity DECIMAL(14, 3) NOT NULL,
    unit_price DECIMAL(14, 2) NULL,
    order_id CHAR(36) NULL,
    return_id CHAR(36) NULL,
    return_reason VARCHAR(100) NULL,
    event_time DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_interaction_events_source_line
        UNIQUE (source_event_id, order_item_id, event_type),
    INDEX idx_interaction_events_source_event_id (source_event_id),
    INDEX idx_interaction_events_customer_time (customer_id, event_time),
    INDEX idx_interaction_events_product_time (product_id, event_time),
    INDEX idx_interaction_events_type_time (event_type, event_time),
    INDEX idx_interaction_events_return_item_id (return_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE customer_interactions (
    customer_id CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    total_purchase_count INT NOT NULL DEFAULT 0,
    total_purchase_quantity DECIMAL(14, 3) NOT NULL DEFAULT 0.000,
    total_return_count INT NOT NULL DEFAULT 0,
    total_return_quantity DECIMAL(14, 3) NOT NULL DEFAULT 0.000,
    last_interaction_time DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (customer_id, product_id),
    INDEX idx_customer_interactions_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
