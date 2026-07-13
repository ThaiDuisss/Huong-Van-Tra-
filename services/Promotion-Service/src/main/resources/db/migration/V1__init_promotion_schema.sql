CREATE TABLE events (
                        id BINARY(16) NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        description TEXT,
                        start_at DATETIME(6) NOT NULL,
                        end_at DATETIME(6),
                        event_type VARCHAR(50) NOT NULL,
                        audience VARCHAR(50) NOT NULL,
                        status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                        is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                        created_by BINARY(16),
                        updated_by BINARY(16),
                        created_at DATETIME(6),
                        updated_at DATETIME(6),
                        version BIGINT,

                        PRIMARY KEY (id)
);

CREATE TABLE promotions (
                            id BINARY(16) NOT NULL,
                            event_id BINARY(16) NOT NULL,

                            name VARCHAR(255) NOT NULL,
                            description TEXT,
                            objective TEXT,
                            type VARCHAR(50) NOT NULL,
                            apply_method VARCHAR(50) NOT NULL,
                            start_at DATETIME(6) NOT NULL,
                            end_at DATETIME(6),
                            is_stackable TINYINT(1) NOT NULL DEFAULT 0,
                            priority INT NOT NULL DEFAULT 0,
                            status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                            is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                            created_by BINARY(16),
                            updated_by BINARY(16),
                            created_at DATETIME(6),
                            updated_at DATETIME(6),
                            version BIGINT,

                            PRIMARY KEY (id),

                            CONSTRAINT fk_promotions_event
                                FOREIGN KEY (event_id)
                                    REFERENCES events (id)
);

CREATE TABLE promotion_targets (
                                   id BINARY(16) NOT NULL,
                                   promotion_id BINARY(16) NOT NULL,

                                   target_type VARCHAR(30) NOT NULL,
                                   target_id BINARY(16) NOT NULL,
                                   import_batch_id BINARY(16),
                                   is_excluded TINYINT(1) NOT NULL DEFAULT 0,
                                   override_benefit_type VARCHAR(50),
                                   override_value DECIMAL(14, 2),
                                   override_max_discount DECIMAL(14, 2),
                                   reason TEXT,

                                   is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                                   created_by BINARY(16),
                                   updated_by BINARY(16),
                                   created_at DATETIME(6),
                                   updated_at DATETIME(6),
                                   version BIGINT,

                                   PRIMARY KEY (id),

                                   CONSTRAINT uk_promotion_target
                                       UNIQUE (promotion_id, target_type, target_id),

                                   CONSTRAINT fk_promotion_targets_promotion
                                       FOREIGN KEY (promotion_id)
                                           REFERENCES promotions (id)
);

CREATE TABLE promotion_conditions (
                                      id BINARY(16) NOT NULL,
                                      promotion_id BINARY(16) NOT NULL,

                                      condition_type VARCHAR(50) NOT NULL,
                                      operator VARCHAR(30) NOT NULL,
                                      condition_value JSON NOT NULL,

                                      is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                                      created_by BINARY(16),
                                      updated_by BINARY(16),
                                      created_at DATETIME(6),
                                      updated_at DATETIME(6),
                                      version BIGINT,

                                      PRIMARY KEY (id),

                                      CONSTRAINT fk_promotion_conditions_promotion
                                          FOREIGN KEY (promotion_id)
                                              REFERENCES promotions (id)
);

CREATE TABLE promotion_benefits (
                                    id BINARY(16) NOT NULL,
                                    promotion_id BINARY(16) NOT NULL,

                                    benefit_type VARCHAR(50) NOT NULL,
                                    value DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                                    max_discount DECIMAL(14, 2),
                                    gift_product_id BINARY(16),
                                    buy_quantity INT,
                                    get_quantity INT,
                                    reason TEXT,
                                    min_margin_percent DECIMAL(5, 2),
                                    expected_margin_percent DECIMAL(5, 2),
                                    recommended_value DECIMAL(14, 2),
                                    recommend_reason TEXT,
                                    confidence_score DECIMAL(5, 2),
                                    ai_recommended TINYINT(1) NOT NULL DEFAULT 0,

                                    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                                    created_by BINARY(16),
                                    updated_by BINARY(16),
                                    created_at DATETIME(6),
                                    updated_at DATETIME(6),
                                    version BIGINT,

                                    PRIMARY KEY (id),

                                    CONSTRAINT fk_promotion_benefits_promotion
                                        FOREIGN KEY (promotion_id)
                                            REFERENCES promotions (id)
);

CREATE TABLE coupons (
                         id BINARY(16) NOT NULL,
                         promotion_id BINARY(16) NOT NULL,

                         scope VARCHAR(30) NOT NULL,
                         customer_id BINARY(16),
                         code VARCHAR(100) NOT NULL,
                         issue_reason VARCHAR(50),
                         source_order_id BINARY(16),
                         issued_at DATETIME(6),
                         claimed_at DATETIME(6),
                         usage_limit INT NOT NULL DEFAULT 1,
                         used_count INT NOT NULL DEFAULT 0,
                         per_customer_limit INT NOT NULL DEFAULT 1,
                         expires_at DATETIME(6),
                         status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                         snapshot_promotion_name VARCHAR(255),
                         snapshot_benefit_type VARCHAR(50),
                         snapshot_discount_value DECIMAL(14, 2),
                         snapshot_max_discount DECIMAL(14, 2),

                         is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                         created_by BINARY(16),
                         updated_by BINARY(16),
                         created_at DATETIME(6),
                         updated_at DATETIME(6),
                         version BIGINT,

                         PRIMARY KEY (id),

                         CONSTRAINT uk_coupons_code
                             UNIQUE (code),

                         CONSTRAINT fk_coupons_promotion
                             FOREIGN KEY (promotion_id)
                                 REFERENCES promotions (id)
);

CREATE TABLE coupon_usages (
                               id BINARY(16) NOT NULL,
                               coupon_id BINARY(16) NOT NULL,

                               order_id BINARY(16) NOT NULL,
                               customer_id BINARY(16),
                               discount_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                               status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
                               reservation_token VARCHAR(100),
                               reserved_at DATETIME(6),
                               reservation_expires_at DATETIME(6),
                               used_at DATETIME(6),
                               released_at DATETIME(6),
                               branch_id BINARY(16),
                               cashier_id BINARY(16),
                               channel VARCHAR(30),

                               is_deleted TINYINT(1) NOT NULL DEFAULT 0,
                               created_by BINARY(16),
                               updated_by BINARY(16),
                               created_at DATETIME(6),
                               updated_at DATETIME(6),
                               version BIGINT,

                               PRIMARY KEY (id),

                               CONSTRAINT uk_coupon_usages_reservation_token
                                   UNIQUE (reservation_token),

                               CONSTRAINT fk_coupon_usages_coupon
                                   FOREIGN KEY (coupon_id)
                                       REFERENCES coupons (id)
);
