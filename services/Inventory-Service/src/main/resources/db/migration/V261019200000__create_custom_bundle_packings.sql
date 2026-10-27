-- ====================================================================
-- Migration V261019200000: Đóng trà theo yêu cầu (Custom Tea Bundle)
--   - custom_bundle_packings: yêu cầu đóng gói cho một dòng bundle của đơn bán
--     (WAITING_MATERIAL -> AWAITING_PACKING -> PACKED | CANCELLED)
--   - custom_bundle_packing_lines: nguyên liệu khách chọn
--   Mã yêu cầu "DGB-00001" và mã xuất kho "PXB-00001" sinh qua business_sequences (tự thêm khóa khi dùng).
-- ====================================================================

CREATE TABLE IF NOT EXISTS custom_bundle_packings (
    id BINARY(16) NOT NULL,
    packing_code VARCHAR(100) NOT NULL,
    order_id BINARY(16) NOT NULL,
    order_code VARCHAR(100) NULL,
    order_item_id BINARY(16) NOT NULL,
    product_variant_id BINARY(16) NOT NULL,
    bundle_name VARCHAR(255) NULL,
    branch_id BINARY(16) NULL,
    location_id BINARY(16) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'WAITING_MATERIAL',
    expected_total_weight DECIMAL(18,4) NOT NULL DEFAULT 0,
    actual_total_weight DECIMAL(18,4) NULL,
    packaging_type VARCHAR(100) NULL,
    number_of_packages INT NULL,
    packing_note VARCHAR(1000) NULL,
    expected_available_at DATETIME NULL,
    reserved_at DATETIME NULL,
    packed_by BINARY(16) NULL,
    packed_at DATETIME NULL,
    issue_transaction_code VARCHAR(100) NULL,
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
    UNIQUE KEY uq_custom_bundle_packings_code (packing_code),
    UNIQUE KEY uq_custom_bundle_packings_order_item (order_item_id),
    INDEX idx_custom_bundle_packings_status (status),
    INDEX idx_custom_bundle_packings_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS custom_bundle_packing_lines (
    id BINARY(16) NOT NULL,
    packing_id BINARY(16) NOT NULL,
    ingredient_id BINARY(16) NOT NULL,
    inventory_item_id BINARY(16) NOT NULL,
    ingredient_name VARCHAR(255) NULL,
    required_quantity DECIMAL(18,4) NOT NULL,
    picked BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BINARY(16) NULL,
    updated_by BINARY(16) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    PRIMARY KEY (id),
    INDEX idx_custom_bundle_packing_lines_packing (packing_id),
    INDEX idx_custom_bundle_packing_lines_item (inventory_item_id),
    CONSTRAINT fk_custom_bundle_packing_lines_packing FOREIGN KEY (packing_id)
        REFERENCES custom_bundle_packings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
