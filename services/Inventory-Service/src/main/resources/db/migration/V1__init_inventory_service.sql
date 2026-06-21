CREATE TABLE inventory_items (
    id CHAR(36) PRIMARY KEY,
    reference_id BIGINT NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    base_unit_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    
    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_inventory_items_ref_type (reference_id, item_type),
    INDEX idx_inventory_items_reference (reference_id),
    INDEX idx_inventory_items_status (status)
);

CREATE TABLE inventory_locations (
    id CHAR(36) PRIMARY KEY,
    branch_id CHAR(36) NOT NULL,
    parent_location_id CHAR(36) NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_inventory_locations_branch_code (branch_id, code),
    CONSTRAINT fk_inventory_locations_parent FOREIGN KEY (parent_location_id) REFERENCES inventory_locations(id) ON DELETE SET NULL,
    INDEX idx_inventory_locations_branch (branch_id),
    INDEX idx_inventory_locations_parent (parent_location_id)
);

CREATE TABLE inventory_balances (
    id CHAR(36) PRIMARY KEY,
    inventory_item_id CHAR(36) NOT NULL,
    location_id CHAR(36) NOT NULL,
    quantity_on_hand DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    quantity_reserved DECIMAL(18, 2) NOT NULL DEFAULT 0.00,

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_inventory_balances_item_loc (inventory_item_id, location_id),
    CONSTRAINT fk_inventory_balances_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_balances_location FOREIGN KEY (location_id) REFERENCES inventory_locations(id) ON DELETE CASCADE,
    INDEX idx_inventory_balances_item (inventory_item_id),
    INDEX idx_inventory_balances_location (location_id)
);

CREATE TABLE inventory_transactions (
    id CHAR(36) PRIMARY KEY,
    transaction_code VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(50) NULL,
    branch_id CHAR(36) NOT NULL,

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    UNIQUE KEY uq_inventory_transactions_code (transaction_code),
    INDEX idx_inventory_transactions_code (transaction_code),
    INDEX idx_inventory_transactions_type (type),
    INDEX idx_inventory_transactions_ref (reference_type, reference_id),
    INDEX idx_inventory_transactions_branch (branch_id)
);

CREATE TABLE inventory_transaction_lines (
    id CHAR(36) PRIMARY KEY,
    transaction_id CHAR(36) NOT NULL,
    inventory_item_id CHAR(36) NOT NULL,
    location_id CHAR(36) NOT NULL,
    quantity DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    before_quantity DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    after_quantity DECIMAL(18, 2) NOT NULL DEFAULT 0.00,

    created_by CHAR(36) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_inventory_trans_lines_trans FOREIGN KEY (transaction_id) REFERENCES inventory_transactions(id) ON DELETE CASCADE,
    CONSTRAINT fk_inventory_trans_lines_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
    CONSTRAINT fk_inventory_trans_lines_location FOREIGN KEY (location_id) REFERENCES inventory_locations(id),
    INDEX idx_inventory_trans_lines_trans (transaction_id),
    INDEX idx_inventory_trans_lines_item (inventory_item_id),
    INDEX idx_inventory_trans_lines_location (location_id)
);
