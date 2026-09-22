-- Phase 2A: durable customer-return disposal approval workflow.
-- The customer return/inbox baseline tables predate the Flyway history in this
-- module. This migration intentionally extends that installed baseline and does
-- not attempt to recreate it.

CREATE TABLE IF NOT EXISTS disposal_requests (
    id BINARY(16) NOT NULL,
    request_code VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) NOT NULL DEFAULT 'CUSTOMER_RETURN_INSPECTION',
    source_inspection_id BINARY(16) NOT NULL,
    return_id BINARY(16) NOT NULL,
    return_code VARCHAR(50) NOT NULL,
    branch_id BINARY(16) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_by BINARY(16) NULL,
    approved_at DATETIME(6) NULL,
    transaction_id BINARY(16) NULL,
    note VARCHAR(500) NULL,
    created_by BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by BINARY(16) NULL,
    updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_disposal_request_code (request_code),
    UNIQUE KEY uq_disposal_source_inspection (source_inspection_id),
    UNIQUE KEY uq_disposal_transaction (transaction_id),
    INDEX idx_disposal_requests_status (status),
    INDEX idx_disposal_requests_return (return_id),
    INDEX idx_disposal_requests_created_at (created_at),
    CONSTRAINT fk_disposal_source_inspection
        FOREIGN KEY (source_inspection_id) REFERENCES customer_return_inspections(id),
    CONSTRAINT fk_disposal_transaction
        FOREIGN KEY (transaction_id) REFERENCES inventory_transactions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS upgrade_customer_return_disposal_v260920160000;

DELIMITER $$
CREATE PROCEDURE upgrade_customer_return_disposal_v260920160000()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_return_inspections'
          AND column_name = 'command_id'
    ) THEN
        ALTER TABLE customer_return_inspections
            ADD COLUMN command_id BINARY(16) NULL AFTER return_code;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_return_inspections'
          AND index_name = 'uq_customer_return_inspections_command'
    ) THEN
        ALTER TABLE customer_return_inspections
            ADD UNIQUE INDEX uq_customer_return_inspections_command (command_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_return_inspection_lines'
          AND column_name = 'disposal_request_id'
    ) THEN
        ALTER TABLE customer_return_inspection_lines
            ADD COLUMN disposal_request_id BINARY(16) NULL AFTER transaction_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_return_inspection_lines'
          AND index_name = 'idx_customer_return_lines_disposal'
    ) THEN
        ALTER TABLE customer_return_inspection_lines
            ADD INDEX idx_customer_return_lines_disposal (disposal_request_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_return_inspection_lines'
          AND constraint_name = 'fk_customer_return_lines_disposal'
    ) THEN
        ALTER TABLE customer_return_inspection_lines
            ADD CONSTRAINT fk_customer_return_lines_disposal
            FOREIGN KEY (disposal_request_id) REFERENCES disposal_requests(id);
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'inventory_transaction_lines'
          AND column_name = 'location_id'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE inventory_transaction_lines
            MODIFY COLUMN location_id BINARY(16) NULL;
    END IF;
END $$
DELIMITER ;

CALL upgrade_customer_return_disposal_v260920160000();
DROP PROCEDURE IF EXISTS upgrade_customer_return_disposal_v260920160000;
