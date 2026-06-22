ALTER TABLE order_items DROP FOREIGN KEY fk_order_items_order;
ALTER TABLE order_discounts DROP FOREIGN KEY fk_order_discounts_order;
ALTER TABLE order_status_history DROP FOREIGN KEY fk_order_status_history_order;

ALTER TABLE orders DROP PRIMARY KEY;
ALTER TABLE orders
    ADD COLUMN id_binary BINARY(16) NULL,
    ADD COLUMN tenant_id_binary BINARY(16) NULL,
    ADD COLUMN branch_id_binary BINARY(16) NULL,
    ADD COLUMN terminal_id_binary BINARY(16) NULL,
    ADD COLUMN shift_id_binary BINARY(16) NULL,
    ADD COLUMN customer_id_binary BINARY(16) NULL;
UPDATE orders
SET id_binary = UUID_TO_BIN(id),
    tenant_id_binary = IF(tenant_id IS NULL OR tenant_id = '', NULL, UUID_TO_BIN(tenant_id)),
    branch_id_binary = IF(branch_id IS NULL OR branch_id = '', NULL, UUID_TO_BIN(branch_id)),
    terminal_id_binary = IF(terminal_id IS NULL OR terminal_id = '', NULL, UUID_TO_BIN(terminal_id)),
    shift_id_binary = IF(shift_id IS NULL OR shift_id = '', NULL, UUID_TO_BIN(shift_id)),
    customer_id_binary = IF(customer_id IS NULL OR customer_id = '', NULL, UUID_TO_BIN(customer_id));
ALTER TABLE orders
    DROP COLUMN id,
    DROP COLUMN tenant_id,
    DROP COLUMN branch_id,
    DROP COLUMN terminal_id,
    DROP COLUMN shift_id,
    DROP COLUMN customer_id;
ALTER TABLE orders
    CHANGE COLUMN id_binary id BINARY(16) NOT NULL,
    CHANGE COLUMN tenant_id_binary tenant_id BINARY(16) NULL,
    CHANGE COLUMN branch_id_binary branch_id BINARY(16) NULL,
    CHANGE COLUMN terminal_id_binary terminal_id BINARY(16) NULL,
    CHANGE COLUMN shift_id_binary shift_id BINARY(16) NULL,
    CHANGE COLUMN customer_id_binary customer_id BINARY(16) NULL,
    MODIFY COLUMN created_by VARCHAR(255) NULL,
    MODIFY COLUMN updated_by VARCHAR(255) NULL;
ALTER TABLE orders ADD PRIMARY KEY (id);
CREATE INDEX idx_orders_tenant_id ON orders (tenant_id);
CREATE INDEX idx_orders_branch_id ON orders (branch_id);
CREATE INDEX idx_orders_terminal_id ON orders (terminal_id);
CREATE INDEX idx_orders_shift_id ON orders (shift_id);
CREATE INDEX idx_orders_customer_id ON orders (customer_id);

ALTER TABLE order_items DROP PRIMARY KEY;
ALTER TABLE order_items
    ADD COLUMN id_binary BINARY(16) NULL,
    ADD COLUMN order_id_binary BINARY(16) NULL,
    ADD COLUMN product_variant_id_binary BINARY(16) NULL,
    ADD COLUMN bom_version_id_binary BINARY(16) NULL;
UPDATE order_items
SET id_binary = UUID_TO_BIN(id),
    order_id_binary = UUID_TO_BIN(order_id),
    product_variant_id_binary = UUID_TO_BIN(product_variant_id),
    bom_version_id_binary = IF(bom_version_id IS NULL OR bom_version_id = '', NULL, UUID_TO_BIN(bom_version_id));
ALTER TABLE order_items
    DROP COLUMN id,
    DROP COLUMN order_id,
    DROP COLUMN product_variant_id,
    DROP COLUMN bom_version_id;
ALTER TABLE order_items
    CHANGE COLUMN id_binary id BINARY(16) NOT NULL,
    CHANGE COLUMN order_id_binary order_id BINARY(16) NOT NULL,
    CHANGE COLUMN product_variant_id_binary product_variant_id BINARY(16) NOT NULL,
    CHANGE COLUMN bom_version_id_binary bom_version_id BINARY(16) NULL,
    MODIFY COLUMN created_by VARCHAR(255) NULL,
    MODIFY COLUMN updated_by VARCHAR(255) NULL;
ALTER TABLE order_items ADD PRIMARY KEY (id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_variant_id ON order_items (product_variant_id);
CREATE INDEX idx_order_items_bom_version_id ON order_items (bom_version_id);

ALTER TABLE order_discounts DROP PRIMARY KEY;
ALTER TABLE order_discounts
    ADD COLUMN id_binary BINARY(16) NULL,
    ADD COLUMN order_id_binary BINARY(16) NULL,
    ADD COLUMN promotion_id_binary BINARY(16) NULL,
    ADD COLUMN coupon_id_binary BINARY(16) NULL;
UPDATE order_discounts
SET id_binary = UUID_TO_BIN(id),
    order_id_binary = UUID_TO_BIN(order_id),
    promotion_id_binary = IF(promotion_id IS NULL OR promotion_id = '', NULL, UUID_TO_BIN(promotion_id)),
    coupon_id_binary = IF(coupon_id IS NULL OR coupon_id = '', NULL, UUID_TO_BIN(coupon_id));
ALTER TABLE order_discounts
    DROP COLUMN id,
    DROP COLUMN order_id,
    DROP COLUMN promotion_id,
    DROP COLUMN coupon_id;
ALTER TABLE order_discounts
    CHANGE COLUMN id_binary id BINARY(16) NOT NULL,
    CHANGE COLUMN order_id_binary order_id BINARY(16) NOT NULL,
    CHANGE COLUMN promotion_id_binary promotion_id BINARY(16) NULL,
    CHANGE COLUMN coupon_id_binary coupon_id BINARY(16) NULL,
    MODIFY COLUMN created_by VARCHAR(255) NULL,
    MODIFY COLUMN updated_by VARCHAR(255) NULL;
ALTER TABLE order_discounts ADD PRIMARY KEY (id);
CREATE INDEX idx_order_discounts_order_id ON order_discounts (order_id);
CREATE INDEX idx_order_discounts_promotion_id ON order_discounts (promotion_id);
CREATE INDEX idx_order_discounts_coupon_id ON order_discounts (coupon_id);

ALTER TABLE order_status_history DROP PRIMARY KEY;
ALTER TABLE order_status_history
    ADD COLUMN id_binary BINARY(16) NULL,
    ADD COLUMN order_id_binary BINARY(16) NULL,
    ADD COLUMN changed_by_binary BINARY(16) NULL;
UPDATE order_status_history
SET id_binary = UUID_TO_BIN(id),
    order_id_binary = UUID_TO_BIN(order_id),
    changed_by_binary = IF(changed_by IS NULL OR changed_by = '', NULL, UUID_TO_BIN(changed_by));
ALTER TABLE order_status_history
    DROP COLUMN id,
    DROP COLUMN order_id,
    DROP COLUMN changed_by;
ALTER TABLE order_status_history
    CHANGE COLUMN id_binary id BINARY(16) NOT NULL,
    CHANGE COLUMN order_id_binary order_id BINARY(16) NOT NULL,
    CHANGE COLUMN changed_by_binary changed_by BINARY(16) NULL,
    MODIFY COLUMN created_by VARCHAR(255) NULL,
    MODIFY COLUMN updated_by VARCHAR(255) NULL;
ALTER TABLE order_status_history ADD PRIMARY KEY (id);
CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id);
CREATE INDEX idx_order_status_history_changed_by ON order_status_history (changed_by);

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
            ON DELETE CASCADE;
ALTER TABLE order_discounts
    ADD CONSTRAINT fk_order_discounts_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
            ON DELETE CASCADE;
ALTER TABLE order_status_history
    ADD CONSTRAINT fk_order_status_history_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
            ON DELETE CASCADE;
