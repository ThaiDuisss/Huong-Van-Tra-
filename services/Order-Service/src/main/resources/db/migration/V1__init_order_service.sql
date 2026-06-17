CREATE TABLE orders (
                        id CHAR(36) PRIMARY KEY,

                        order_code VARCHAR(50) NOT NULL UNIQUE,

                        tenant_id CHAR(36) NULL,
                        branch_id CHAR(36) NULL,
                        terminal_id CHAR(36) NULL,
                        shift_id CHAR(36) NULL,
                        customer_id CHAR(36) NULL,

                        order_type VARCHAR(50) NOT NULL,
                        status VARCHAR(50) NOT NULL,
                        payment_status VARCHAR(50) NOT NULL,
                        fulfillment_status VARCHAR(50) NULL,

                        original_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                        discount_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                        shipping_fee DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                        final_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                        paid_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                        debt_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,

                        created_by CHAR(36) NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        updated_by CHAR(36) NULL,
                        updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                        version BIGINT NULL,
                        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                        INDEX idx_orders_order_code (order_code),
                        INDEX idx_orders_tenant_id (tenant_id),
                        INDEX idx_orders_branch_id (branch_id),
                        INDEX idx_orders_terminal_id (terminal_id),
                        INDEX idx_orders_shift_id (shift_id),
                        INDEX idx_orders_customer_id (customer_id),
                        INDEX idx_orders_status (status),
                        INDEX idx_orders_payment_status (payment_status),
                        INDEX idx_orders_created_at (created_at)
);

CREATE TABLE order_items (
                             id CHAR(36) PRIMARY KEY,

                             order_id CHAR(36) NOT NULL,
                             product_variant_id CHAR(36) NOT NULL,

                             sku_snapshot VARCHAR(100) NULL,
                             product_name_snapshot VARCHAR(255) NOT NULL,
                             variant_name_snapshot VARCHAR(255) NULL,

                             quantity DECIMAL(14, 3) NOT NULL DEFAULT 0.000,
                             original_unit_price DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                             actual_unit_price DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                             discount_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                             line_total DECIMAL(14, 2) NOT NULL DEFAULT 0.00,

                             bom_version_id CHAR(36) NULL,

                             created_by CHAR(36) NULL,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_by CHAR(36) NULL,
                             updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                             version BIGINT NULL,
                             is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                             CONSTRAINT fk_order_items_order
                                 FOREIGN KEY (order_id) REFERENCES orders(id)
                                     ON DELETE CASCADE,

                             INDEX idx_order_items_order_id (order_id),
                             INDEX idx_order_items_product_variant_id (product_variant_id),
                             INDEX idx_order_items_bom_version_id (bom_version_id)
);

CREATE TABLE order_discounts (
                                 id CHAR(36) PRIMARY KEY,

                                 order_id CHAR(36) NOT NULL,

                                 promotion_id CHAR(36) NULL,
                                 coupon_id CHAR(36) NULL,

                                 discount_type VARCHAR(50) NOT NULL,
                                 discount_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,

                                 snapshot_json JSON NULL,

                                 created_by CHAR(36) NULL,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_by CHAR(36) NULL,
                                 updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                                 version BIGINT NULL,
                                 is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                 CONSTRAINT fk_order_discounts_order
                                     FOREIGN KEY (order_id) REFERENCES orders(id)
                                         ON DELETE CASCADE,

                                 INDEX idx_order_discounts_order_id (order_id),
                                 INDEX idx_order_discounts_promotion_id (promotion_id),
                                 INDEX idx_order_discounts_coupon_id (coupon_id)
);

CREATE TABLE order_status_history (
                                      id CHAR(36) PRIMARY KEY,

                                      order_id CHAR(36) NOT NULL,

                                      from_status VARCHAR(50) NULL,
                                      to_status VARCHAR(50) NOT NULL,
                                      reason VARCHAR(255) NULL,

                                      changed_by CHAR(36) NULL,
                                      changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                      created_by CHAR(36) NULL,
                                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_by CHAR(36) NULL,
                                      updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                                      version BIGINT NULL,
                                      is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                      CONSTRAINT fk_order_status_history_order
                                          FOREIGN KEY (order_id) REFERENCES orders(id)
                                              ON DELETE CASCADE,

                                      INDEX idx_order_status_history_order_id (order_id),
                                      INDEX idx_order_status_history_changed_by (changed_by),
                                      INDEX idx_order_status_history_changed_at (changed_at)
);