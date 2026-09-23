-- ============================================================
-- Migration: Recreate table ingredients and supplier_ingredients if missing
-- Target Database: MySQL 8
-- ============================================================

CREATE TABLE IF NOT EXISTS ingredients (
    id BINARY(16) PRIMARY KEY,
    ingredient_code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    image_url VARCHAR(500) NULL,
    unit_id BINARY(16) NULL,
    reorder_point INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_ingredients_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE SET NULL,
    INDEX idx_ingredients_unit (unit_id),
    INDEX idx_ingredients_code (ingredient_code)
);

CREATE TABLE IF NOT EXISTS supplier_ingredients (
    supplier_id BINARY(16) NOT NULL,
    ingredient_id BINARY(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (supplier_id, ingredient_id),
    CONSTRAINT fk_supplier_ingredients_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_ingredients_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE CASCADE,
    INDEX idx_supplier_ingredients_supplier (supplier_id),
    INDEX idx_supplier_ingredients_ingredient (ingredient_id)
);

-- Đảm bảo sequence key cho INGREDIENT tồn tại trong business_sequences
INSERT INTO business_sequences (sequence_key, current_value) VALUES
('INGREDIENT', 0)
ON DUPLICATE KEY UPDATE current_value = current_value;
