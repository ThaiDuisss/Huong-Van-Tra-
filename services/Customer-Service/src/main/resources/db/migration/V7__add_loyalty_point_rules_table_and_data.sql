CREATE TABLE loyalty_point_rules (
         id                         BINARY(16)      PRIMARY KEY,

         code                       VARCHAR(50)     NOT NULL UNIQUE,
         name                       VARCHAR(150)    NOT NULL,
         description                VARCHAR(500)    NULL,
         tier_id                    BINARY(16)      NULL,

         spend_amount_per_block     DECIMAL(15, 2)  NOT NULL,
         points_per_block           INT             NOT NULL,
         min_order_amount           DECIMAL(15, 2)  NOT NULL DEFAULT 0,

         earn_after_discount        BOOLEAN         NOT NULL DEFAULT TRUE,
         allow_coupon_usage         BOOLEAN         NOT NULL DEFAULT TRUE,

         point_multiplier           DECIMAL(5, 2)   NOT NULL DEFAULT 1.00,
         priority                   INT             NOT NULL DEFAULT 100,

         effective_from             DATETIME        NOT NULL,
         effective_to               DATETIME        NULL,

         status                     VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE', -- DRAFT | ACTIVE | INACTIVE | EXPIRED

         is_deleted                 BOOLEAN         NOT NULL DEFAULT FALSE,
         created_by                 VARCHAR(36)     NULL,
         updated_by                 VARCHAR(36)     NULL,
         created_at                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
         updated_at                 DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
         version                    BIGINT          NULL,

         CONSTRAINT fk_loyalty_point_rules_tier
             FOREIGN KEY (tier_id)
                 REFERENCES customer_tiers(id)
                 ON UPDATE CASCADE
                 ON DELETE SET NULL,

         CONSTRAINT chk_loyalty_point_rules_spend
             CHECK (spend_amount_per_block > 0),

         CONSTRAINT chk_loyalty_point_rules_points
             CHECK (points_per_block > 0),

         CONSTRAINT chk_loyalty_point_rules_min_order
             CHECK (min_order_amount >= 0),

         CONSTRAINT chk_loyalty_point_rules_multiplier
             CHECK (point_multiplier > 0),

         CONSTRAINT chk_loyalty_point_rules_effective_time
             CHECK (
                 effective_to IS NULL
                     OR effective_to > effective_from
                 )
);

CREATE INDEX idx_loyalty_point_rules_status_time
    ON loyalty_point_rules(status, effective_from, effective_to);

CREATE INDEX idx_loyalty_point_rules_tier_id
    ON loyalty_point_rules(tier_id);

CREATE INDEX idx_loyalty_point_rules_priority
    ON loyalty_point_rules(priority);

INSERT INTO loyalty_point_rules (
    id,
    code,
    name,
    description,
    tier_id,
    spend_amount_per_block,
    points_per_block,
    min_order_amount,
    earn_after_discount,
    allow_coupon_usage,
    point_multiplier,
    priority,
    effective_from,
    status
) VALUES (
             UUID_TO_BIN(UUID()),
             'DEFAULT_EARNING_2026',
             'Chính sách tích điểm mặc định',
             'Mỗi 10.000 VNĐ thanh toán hợp lệ được 1 điểm.',
             NULL,
             10000.00,
             1,
             100000.00,
             TRUE,
             TRUE,
             1.00,
             100,
             NOW(),
             'ACTIVE'
         );
