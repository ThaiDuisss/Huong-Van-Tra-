-- ============================================================
-- Migration V261002100000: Lịch sử thay đổi giá của bảng giá.
--   Mỗi dòng là 1 lần giá của 1 Mặt hàng trong 1 bảng giá được
--   tạo / đổi / gỡ (CREATED / UPDATED / REMOVED), hoặc 1 lần bảng giá
--   đổi trạng thái (STATUS_CHANGED, khi đó product_variant_id NULL).
--   Bảng chỉ ghi thêm (append-only); cố ý KHÔNG đặt khóa ngoại và lưu
--   snapshot tên bảng giá / SKU / tên Mặt hàng để lịch sử vẫn đọc được
--   sau khi bảng giá hoặc Mặt hàng bị xóa.
-- ============================================================

CREATE TABLE IF NOT EXISTS price_change_histories (
    id BINARY(16) NOT NULL,
    price_list_id BINARY(16) NOT NULL,
    price_list_name VARCHAR(255) NOT NULL,
    product_variant_id BINARY(16) NULL,
    sku VARCHAR(100) NULL,
    variant_name VARCHAR(255) NULL,
    change_type VARCHAR(50) NOT NULL,
    old_price DECIMAL(18, 2) NULL,
    new_price DECIMAL(18, 2) NULL,
    old_status VARCHAR(50) NULL,
    new_status VARCHAR(50) NULL,
    changed_by BINARY(16) NULL,
    changed_by_name VARCHAR(255) NULL,
    changed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_price_change_histories_list_time (price_list_id, changed_at),
    INDEX idx_price_change_histories_list_variant_time (price_list_id, product_variant_id, changed_at),
    INDEX idx_price_change_histories_variant_time (product_variant_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
