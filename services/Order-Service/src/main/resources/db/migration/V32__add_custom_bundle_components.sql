-- Đóng trà theo yêu cầu (Custom Tea Bundle): dòng bundle trong đơn bán + nguyên liệu khách chọn.
ALTER TABLE order_items
    ADD COLUMN item_type VARCHAR(30) NOT NULL DEFAULT 'PRODUCT',
    ADD COLUMN packing_status VARCHAR(30) NULL,
    ADD COLUMN packing_reference_id BINARY(16) NULL,
    ADD COLUMN packing_code VARCHAR(100) NULL;

CREATE INDEX idx_order_items_item_type ON order_items (item_type);

CREATE TABLE order_item_components (
    id BINARY(16) NOT NULL,
    order_item_id BINARY(16) NOT NULL,
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
    CONSTRAINT fk_order_item_components_item
        FOREIGN KEY (order_item_id) REFERENCES order_items (id) ON DELETE CASCADE,
    INDEX idx_order_item_components_item (order_item_id)
);
