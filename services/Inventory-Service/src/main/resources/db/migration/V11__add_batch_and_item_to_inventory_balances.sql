-- =====================================================================
-- Migration V11: Đảm bảo inventory_balances có cả inventory_item_id và inventory_batch_id
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

-- 2. Stored Procedure nâng cấp an toàn cho bảng inventory_balances
DROP PROCEDURE IF EXISTS upgrade_inventory_balances_v11;

DELIMITER $$
CREATE PROCEDURE upgrade_inventory_balances_v11()
BEGIN
    -- Thêm cột inventory_batch_id nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_balances' 
          AND column_name = 'inventory_batch_id'
    ) THEN
        ALTER TABLE inventory_balances ADD COLUMN inventory_batch_id BINARY(16) NULL AFTER id;
    END IF;

    -- Thêm cột inventory_item_id nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_balances' 
          AND column_name = 'inventory_item_id'
    ) THEN
        ALTER TABLE inventory_balances ADD COLUMN inventory_item_id BINARY(16) NULL AFTER id;
    END IF;

    -- Tạo default batch cho các item đang có trong inventory_balances
    INSERT INTO inventory_batches (id, inventory_item_id, batch_code, status, is_deleted)
    SELECT 
        UNHEX(REPLACE(UUID(), '-', '')),
        ib.inventory_item_id,
        CONCAT('DEFAULT-', HEX(SUBSTRING(ib.inventory_item_id, 1, 4))),
        'AVAILABLE',
        FALSE
    FROM inventory_balances ib
    WHERE ib.inventory_batch_id IS NULL AND ib.inventory_item_id IS NOT NULL
    GROUP BY ib.inventory_item_id
    ON DUPLICATE KEY UPDATE status = status;

    -- Cập nhật inventory_batch_id từ inventory_batches
    UPDATE inventory_balances ib
    JOIN inventory_batches batch ON batch.inventory_item_id = ib.inventory_item_id
    SET ib.inventory_batch_id = batch.id
    WHERE ib.inventory_batch_id IS NULL;

    -- Ngược lại, nếu có inventory_batch_id mà inventory_item_id null thì cập nhật ngược lại
    UPDATE inventory_balances ib
    JOIN inventory_batches batch ON batch.id = ib.inventory_batch_id
    SET ib.inventory_item_id = batch.inventory_item_id
    WHERE ib.inventory_item_id IS NULL;

    -- Modify cả 2 cột thành NOT NULL
    ALTER TABLE inventory_balances MODIFY COLUMN inventory_batch_id BINARY(16) NOT NULL;
    ALTER TABLE inventory_balances MODIFY COLUMN inventory_item_id BINARY(16) NOT NULL;

    -- Drop index cũ uq_inventory_balances_item_loc nếu tồn tại
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_balances' 
          AND index_name = 'uq_inventory_balances_item_loc'
    ) THEN
        ALTER TABLE inventory_balances DROP INDEX uq_inventory_balances_item_loc;
    END IF;

    -- Add unique index (inventory_batch_id, location_id) nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_balances' 
          AND index_name = 'uq_inventory_balances_batch_loc'
    ) THEN
        ALTER TABLE inventory_balances ADD CONSTRAINT uq_inventory_balances_batch_loc UNIQUE (inventory_batch_id, location_id);
    END IF;

    -- Add index idx_inventory_balances_item nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_balances' 
          AND index_name = 'idx_inventory_balances_item'
    ) THEN
        ALTER TABLE inventory_balances ADD INDEX idx_inventory_balances_item (inventory_item_id);
    END IF;

    -- Add index idx_inventory_balances_batch nếu chưa có
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics 
        WHERE table_schema = DATABASE() 
          AND table_name = 'inventory_balances' 
          AND index_name = 'idx_inventory_balances_batch'
    ) THEN
        ALTER TABLE inventory_balances ADD INDEX idx_inventory_balances_batch (inventory_batch_id);
    END IF;

END $$
DELIMITER ;

CALL upgrade_inventory_balances_v11();
DROP PROCEDURE IF EXISTS upgrade_inventory_balances_v11;
