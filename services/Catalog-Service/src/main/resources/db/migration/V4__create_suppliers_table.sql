-- ============================================================
-- Migration V4: Tạo bảng suppliers và supplier_ingredients
-- ============================================================

CREATE TABLE IF NOT EXISTS suppliers (
    id BINARY(16) PRIMARY KEY,
    supplier_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    contact_name VARCHAR(100) NULL,
    phone VARCHAR(50) NULL,
    email VARCHAR(100) NULL,
    address VARCHAR(500) NULL,
    tax_code VARCHAR(50) NULL,
    notes TEXT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    INDEX idx_suppliers_code (supplier_code),
    INDEX idx_suppliers_status (status)
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

-- Initialize sequence key for SUPPLIER
INSERT INTO business_sequences (sequence_key, current_value) VALUES
('SUPPLIER', 0)
ON DUPLICATE KEY UPDATE current_value = current_value;
