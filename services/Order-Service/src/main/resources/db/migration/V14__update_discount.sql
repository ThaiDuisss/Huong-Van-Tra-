CREATE TABLE order_discounts
(
    id                       BINARY(16)     NOT NULL,

    order_id                 BINARY(16)     NOT NULL,
    order_item_id            BINARY(16)     NULL,

    scope                    VARCHAR(30)    NOT NULL,
    source                   VARCHAR(30)    NOT NULL,
    discount_type            VARCHAR(30)    NOT NULL,

    discount_value           DECIMAL(19, 4) NOT NULL,
    base_amount              DECIMAL(14, 2) NULL,
    discount_amount          DECIMAL(14, 2) NOT NULL,

    reason_code              VARCHAR(255)   NOT NULL,
    note                     VARCHAR(500)   NULL,

    status                   VARCHAR(30)    NOT NULL,

    applied_by               BINARY(16)     NULL,
    applied_by_name_snapshot VARCHAR(255)   NULL,

    created_at               DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                           ON UPDATE CURRENT_TIMESTAMP(6),
    created_by               VARCHAR(255)   NULL,
    updated_by               VARCHAR(255)   NULL,
    is_deleted               BOOLEAN        NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_order_discounts
        PRIMARY KEY (id),

    CONSTRAINT fk_order_discounts_order
        FOREIGN KEY (order_id)
            REFERENCES orders (id),

    CONSTRAINT fk_order_discounts_order_item
        FOREIGN KEY (order_item_id)
            REFERENCES order_items (id),

    CONSTRAINT chk_order_discounts_discount_value
        CHECK (discount_value >= 0),

    CONSTRAINT chk_order_discounts_base_amount
        CHECK (base_amount IS NULL OR base_amount >= 0),

    CONSTRAINT chk_order_discounts_discount_amount
        CHECK (discount_amount >= 0),

    CONSTRAINT chk_order_discounts_applied_amount
        CHECK (
            base_amount IS NULL
                OR discount_amount <= base_amount
            )
);

CREATE INDEX idx_order_discounts_order_id
    ON order_discounts (order_id);

CREATE INDEX idx_order_discounts_order_item_id
    ON order_discounts (order_item_id);

CREATE INDEX idx_order_discounts_order_scope_status
    ON order_discounts (order_id, scope, status);

CREATE INDEX idx_order_discounts_reason_code
    ON order_discounts (reason_code);