SET @schema_name = DATABASE();

-- =========================
-- Add snapshot columns safely
-- =========================

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN customer_name_snapshot VARCHAR(255) NULL AFTER customer_id',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'customer_name_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN customer_phone_snapshot VARCHAR(30) NULL AFTER customer_name_snapshot',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'customer_phone_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN customer_email_snapshot VARCHAR(255) NULL AFTER customer_phone_snapshot',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'customer_email_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN branch_name_snapshot VARCHAR(255) NULL AFTER branch_id',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'branch_name_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN terminal_name_snapshot VARCHAR(255) NULL AFTER terminal_id',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'terminal_name_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN shift_code_snapshot VARCHAR(100) NULL AFTER shift_id',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'shift_code_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN cashier_id BINARY(16) NULL AFTER customer_email_snapshot',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'cashier_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN cashier_name_snapshot VARCHAR(255) NULL AFTER cashier_id',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'cashier_name_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN recipient_name_snapshot VARCHAR(255) NULL AFTER cashier_name_snapshot',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'recipient_name_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN recipient_phone_snapshot VARCHAR(30) NULL AFTER recipient_name_snapshot',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'recipient_phone_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN shipping_address_snapshot VARCHAR(500) NULL AFTER recipient_phone_snapshot',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'shipping_address_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE orders ADD COLUMN shipping_provider_snapshot VARCHAR(255) NULL AFTER shipping_address_snapshot',
              'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND COLUMN_NAME = 'shipping_provider_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =========================
-- Create indexes safely
-- =========================

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_orders_customer_phone_snapshot ON orders (customer_phone_snapshot)',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_customer_phone_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_orders_customer_name_snapshot ON orders (customer_name_snapshot)',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_customer_name_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_orders_cashier_id ON orders (cashier_id)',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_cashier_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_orders_cashier_name_snapshot ON orders (cashier_name_snapshot)',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_cashier_name_snapshot'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_orders_branch_id ON orders (branch_id)',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_branch_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_orders_created_at ON orders (created_at)',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_created_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_orders_order_type ON orders (order_type)',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_order_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_orders_status ON orders (status)',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(COUNT(*) = 0,
              'CREATE INDEX idx_orders_payment_status ON orders (payment_status)',
              'SELECT 1')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'orders'
      AND INDEX_NAME = 'idx_orders_payment_status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;