-- Migration V2: Convert branch_id in inventory_locations and inventory_transactions from CHAR(36) to BINARY(16)
-- Target Database: MySQL 8

-- 1. inventory_locations: Convert branch_id
ALTER TABLE inventory_locations DROP INDEX idx_inventory_locations_branch;
ALTER TABLE inventory_locations DROP INDEX uq_inventory_locations_branch_code;

ALTER TABLE inventory_locations ADD COLUMN branch_id_bin BINARY(16) NULL;
UPDATE inventory_locations SET branch_id_bin = UUID_TO_BIN(branch_id);
ALTER TABLE inventory_locations MODIFY branch_id_bin BINARY(16) NOT NULL;

ALTER TABLE inventory_locations DROP COLUMN branch_id;
ALTER TABLE inventory_locations RENAME COLUMN branch_id_bin TO branch_id;

CREATE INDEX idx_inventory_locations_branch ON inventory_locations(branch_id);
ALTER TABLE inventory_locations ADD UNIQUE KEY uq_inventory_locations_branch_code (branch_id, code);


-- 2. inventory_transactions: Convert branch_id
ALTER TABLE inventory_transactions DROP INDEX idx_inventory_transactions_branch;

ALTER TABLE inventory_transactions ADD COLUMN branch_id_bin BINARY(16) NULL;
UPDATE inventory_transactions SET branch_id_bin = UUID_TO_BIN(branch_id);
ALTER TABLE inventory_transactions MODIFY branch_id_bin BINARY(16) NOT NULL;

ALTER TABLE inventory_transactions DROP COLUMN branch_id;
ALTER TABLE inventory_transactions RENAME COLUMN branch_id_bin TO branch_id;

CREATE INDEX idx_inventory_transactions_branch ON inventory_transactions(branch_id);
