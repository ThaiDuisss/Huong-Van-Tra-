-- Protect the mandatory customer-return inspection line -> inventory item relation.
-- Existing orphan test aggregates must be removed before this migration runs.

DROP PROCEDURE IF EXISTS add_customer_return_inventory_item_fk_v260920230000;

DELIMITER $$
CREATE PROCEDURE add_customer_return_inventory_item_fk_v260920230000()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM customer_return_inspection_lines line
        LEFT JOIN inventory_items item
            ON item.id = line.inventory_item_id
        WHERE item.id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot add customer return inventory item FK while orphan rows exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'customer_return_inspection_lines'
          AND constraint_name = 'fk_customer_return_inspection_lines_inventory_item'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.key_column_usage key_column
        JOIN information_schema.referential_constraints reference_constraint
          ON reference_constraint.constraint_schema = key_column.constraint_schema
         AND reference_constraint.constraint_name = key_column.constraint_name
         AND reference_constraint.table_name = key_column.table_name
        WHERE key_column.table_schema = DATABASE()
          AND key_column.table_name = 'customer_return_inspection_lines'
          AND key_column.constraint_name = 'fk_customer_return_inspection_lines_inventory_item'
          AND key_column.column_name = 'inventory_item_id'
          AND key_column.referenced_table_name = 'inventory_items'
          AND key_column.referenced_column_name = 'id'
          AND reference_constraint.delete_rule IN ('RESTRICT', 'NO ACTION')
          AND reference_constraint.update_rule IN ('RESTRICT', 'NO ACTION')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Existing customer return inventory item FK has an unexpected definition';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.key_column_usage key_column
        JOIN information_schema.referential_constraints reference_constraint
          ON reference_constraint.constraint_schema = key_column.constraint_schema
         AND reference_constraint.constraint_name = key_column.constraint_name
         AND reference_constraint.table_name = key_column.table_name
        WHERE key_column.table_schema = DATABASE()
          AND key_column.table_name = 'customer_return_inspection_lines'
          AND key_column.constraint_name = 'fk_customer_return_inspection_lines_inventory_item'
          AND key_column.column_name = 'inventory_item_id'
          AND key_column.referenced_table_name = 'inventory_items'
          AND key_column.referenced_column_name = 'id'
          AND reference_constraint.delete_rule IN ('RESTRICT', 'NO ACTION')
          AND reference_constraint.update_rule IN ('RESTRICT', 'NO ACTION')
    ) THEN
        ALTER TABLE customer_return_inspection_lines
            ADD CONSTRAINT fk_customer_return_inspection_lines_inventory_item
            FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id)
            ON DELETE RESTRICT
            ON UPDATE RESTRICT;
    END IF;
END $$
DELIMITER ;

CALL add_customer_return_inventory_item_fk_v260920230000();
DROP PROCEDURE IF EXISTS add_customer_return_inventory_item_fk_v260920230000;
