ALTER TABLE order_return_inventory_commands
    ADD KEY idx_order_return_inventory_commands_reconciliation (status, is_deleted, created_at);
