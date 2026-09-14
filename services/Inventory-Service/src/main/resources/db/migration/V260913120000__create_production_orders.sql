-- ====================================================================
-- Migration V260913120000: Tạo module Lệnh sản xuất (Production Order)
--   - production_order_sequences: sinh mã "LSX-00001"
--   - production_orders: lệnh sản xuất theo product variant + BOM
--   - production_order_lines: dòng nguyên liệu (materialize từ BOM)
--   - production_order_material_allocations: phân bổ FEFO theo batch
-- ====================================================================

CREATE TABLE IF NOT EXISTS production_order_sequences (
    sequence_key VARCHAR(50) NOT NULL,
    current_value BIGINT NOT NULL,
    PRIMARY KEY (sequence_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS production_orders (
    id BINARY(16) NOT NULL,
    order_code VARCHAR(100) NOT NULL,
    product_variant_id BINARY(16) NOT NULL,
    bom_id BINARY(16) NOT NULL,
    bom_version INT NULL,
    planned_quantity DECIMAL(18,2) NOT NULL,
    unit_id BINARY(16) NULL,
    location_id BINARY(16) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    note VARCHAR(1000) NULL,
    actual_good_quantity DECIMAL(18,2) NULL,
    actual_rejected_quantity DECIMAL(18,2) NULL,
    finished_batch_id BINARY(16) NULL,
    submitted_by BINARY(16) NULL,
    submitted_at DATETIME NULL,
    approved_by BINARY(16) NULL,
    approved_at DATETIME NULL,
    released_by BINARY(16) NULL,
    released_at DATETIME NULL,
    started_by BINARY(16) NULL,
    started_at DATETIME NULL,
    completed_by BINARY(16) NULL,
    completed_at DATETIME NULL,
    cancelled_by BINARY(16) NULL,
    cancelled_at DATETIME NULL,
    cancel_reason VARCHAR(500) NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BINARY(16) NULL,
    updated_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_production_orders_code (order_code),
    INDEX idx_production_orders_status (status),
    INDEX idx_production_orders_variant (product_variant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS production_order_lines (
    id BINARY(16) NOT NULL,
    production_order_id BINARY(16) NOT NULL,
    inventory_item_id BINARY(16) NOT NULL,
    ingredient_id BINARY(16) NULL,
    ingredient_name VARCHAR(255) NULL,
    required_quantity DECIMAL(18,4) NOT NULL,
    unit_id BINARY(16) NULL,
    waste_rate DECIMAL(9,4) NULL,
    reserved_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    consumed_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BINARY(16) NULL,
    updated_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    PRIMARY KEY (id),
    INDEX idx_prod_order_lines_order (production_order_id),
    INDEX idx_prod_order_lines_item (inventory_item_id),
    CONSTRAINT fk_prod_order_lines_order FOREIGN KEY (production_order_id) REFERENCES production_orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS production_order_material_allocations (
    id BINARY(16) NOT NULL,
    production_order_line_id BINARY(16) NOT NULL,
    inventory_batch_id BINARY(16) NOT NULL,
    reserved_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    consumed_quantity DECIMAL(18,4) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'RESERVED',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BINARY(16) NULL,
    updated_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_prod_order_alloc_line_batch (production_order_line_id, inventory_batch_id),
    INDEX idx_prod_order_alloc_batch (inventory_batch_id),
    CONSTRAINT fk_prod_order_alloc_line FOREIGN KEY (production_order_line_id) REFERENCES production_order_lines(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
