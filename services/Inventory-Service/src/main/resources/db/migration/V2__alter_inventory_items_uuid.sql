-- Flyway migration to alter inventory_items columns to UUID (CHAR(36))

ALTER TABLE inventory_items MODIFY COLUMN reference_id CHAR(36) NOT NULL;
ALTER TABLE inventory_items MODIFY COLUMN base_unit_id CHAR(36) NOT NULL;
