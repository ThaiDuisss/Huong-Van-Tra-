-- =====================================================================
-- Migration V10: Chuyển đổi inventory_balances liên kết qua INVENTORY_BATCH
-- =====================================================================

-- 1. Đảm bảo bảng inventory_batches đã tồn tại
CREATE TABLE IF NOT EXISTS inventory_batches (
    id BINARY(16) PRIMARY KEY,
    inventory_item_id BINARY(16) NOT NULL,
    batch_code VARCHAR(100) NOT NULL,
    supplier_batch_code VARCHAR(100) NULL,
    manufacture_date DATE NULL,
    expiry_date DATE NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    source_type VARCHAR(50) NULL,
    source_reference_id VARCHAR(100) NULL,
    received_at DATETIME(6) NULL,
    created_by BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by BINARY(16) NULL,
    updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_batches_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
    UNIQUE KEY uq_batches_item_code (inventory_item_id, batch_code),
    INDEX idx_batches_item_status (inventory_item_id, status),
    INDEX idx_batches_expiry (expiry_date),
    INDEX idx_batches_source (source_type, source_reference_id)
);

-- 2. Thêm cột inventory_batch_id vào inventory_balances nếu chưa có
ALTER TABLE inventory_balances
    ADD COLUMN IF NOT EXISTS inventory_batch_id BINARY(16) NULL AFTER id;

-- 3. Tạo default batch cho các item đang có trong inventory_balances và cập nhật inventory_batch_id
INSERT INTO inventory_batches (id, inventory_item_id, batch_code, status, is_deleted)
SELECT 
    UNHEX(REPLACE(UUID(), '-', '')),
    ib.inventory_item_id,
    CONCAT('DEFAULT-', HEX(SUBSTRING(ib.inventory_item_id, 1, 4))),
    'AVAILABLE',
    FALSE
FROM inventory_balances ib
WHERE ib.inventory_batch_id IS NULL
GROUP BY ib.inventory_item_id
ON DUPLICATE KEY UPDATE status = status;

UPDATE inventory_balances ib
JOIN inventory_batches batch ON batch.inventory_item_id = ib.inventory_item_id
SET ib.inventory_batch_id = batch.id
WHERE ib.inventory_batch_id IS NULL;

-- 4. Cho phép inventory_item_id có thể NULL hoặc drop ràng buộc cũ
ALTER TABLE inventory_balances
    MODIFY COLUMN inventory_batch_id BINARY(16) NOT NULL;

-- Drop foreign key cũ và unique key cũ nếu tồn tại
-- Lưu ý: Sử dụng lệnh an toàn hoặc điều kiện
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'inventory_balances' AND constraint_name = 'fk_inventory_balances_item');
SET @drop_fk = IF(@fk_exists > 0, 'ALTER TABLE inventory_balances DROP FOREIGN KEY fk_inventory_balances_item', 'SELECT 1');
PREPARE stmt FROM @drop_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @uq_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'inventory_balances' AND index_name = 'uq_inventory_balances_item_loc');
SET @drop_uq = IF(@uq_exists > 0, 'ALTER TABLE inventory_balances DROP INDEX uq_inventory_balances_item_loc', 'SELECT 1');
PREPARE stmt FROM @drop_uq;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5. Thêm foreign key và unique key mới cho inventory_batch_id
ALTER TABLE inventory_balances
    ADD CONSTRAINT fk_inventory_balances_batch
        FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id) ON DELETE CASCADE,
    ADD CONSTRAINT uq_inventory_balances_batch_loc
        UNIQUE (inventory_batch_id, location_id),
    ADD INDEX idx_inventory_balances_batch (inventory_batch_id);
