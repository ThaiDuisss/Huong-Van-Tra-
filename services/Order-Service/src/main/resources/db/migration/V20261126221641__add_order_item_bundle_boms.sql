-- Multi-BOM Custom Tea Bundle: lưu danh sách công thức (BOM), ghi chú riêng theo từng BOM và khóa idempotency.
ALTER TABLE order_items
    ADD COLUMN bundle_request_id BINARY(16) NULL;

CREATE UNIQUE INDEX uk_order_items_bundle_request_id
    ON order_items (bundle_request_id);

CREATE TABLE order_item_bundle_boms (
    id BINARY(16) NOT NULL,
    order_item_id BINARY(16) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    source_variant_id BINARY(16) NOT NULL,
    bom_sku_snapshot VARCHAR(100) NULL,
    bom_name_snapshot VARCHAR(255) NOT NULL,
    note VARCHAR(1000) NULL,
    estimated_price DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_bundle_boms_item
        FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE,
    CONSTRAINT uk_order_item_bundle_boms_variant
        UNIQUE (order_item_id, source_variant_id),
    INDEX idx_order_item_bundle_boms_item (order_item_id)
);

CREATE TABLE order_item_bundle_bom_lines (
    id BINARY(16) NOT NULL,
    bundle_bom_id BINARY(16) NOT NULL,
    ingredient_id BINARY(16) NOT NULL,
    ingredient_code_snapshot VARCHAR(100) NULL,
    ingredient_name_snapshot VARCHAR(255) NOT NULL,
    unit_snapshot VARCHAR(50) NULL,
    quantity DECIMAL(14, 3) NOT NULL,
    unit_price DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    line_total DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    created_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BINARY(16) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_bundle_bom_lines_bom
        FOREIGN KEY (bundle_bom_id) REFERENCES order_item_bundle_boms (id) ON DELETE CASCADE,
    INDEX idx_order_item_bundle_bom_lines_bom (bundle_bom_id)
);
