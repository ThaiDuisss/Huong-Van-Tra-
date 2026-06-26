-- =========================================================
-- V1__init_promotion_schema.sql
-- Promotion Service Schema
-- =========================================================


-- =========================================================
-- 1. EVENTS
-- =========================================================

CREATE TABLE events (
                        id BINARY(16) PRIMARY KEY,

                        name VARCHAR(255) NOT NULL,
                        description TEXT NULL,

                        start_at DATETIME NOT NULL,
                        end_at DATETIME NULL,

                        event_type VARCHAR(50) NOT NULL,
                        audience VARCHAR(50) NOT NULL,

                        status VARCHAR(30) NOT NULL DEFAULT 'INACTIVE',

                        created_by BINARY(16) NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        updated_by BINARY(16) NULL,
                        updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                        version BIGINT NOT NULL DEFAULT 0,
                        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                        INDEX idx_events_status (status),
                        INDEX idx_events_type (event_type),
                        INDEX idx_events_audience (audience),
                        INDEX idx_events_start_at (start_at),
                        INDEX idx_events_end_at (end_at),
                        INDEX idx_events_status_time (status, start_at, end_at),
                        INDEX idx_events_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 2. PROMOTIONS
-- =========================================================

CREATE TABLE promotions (
                            id BINARY(16) PRIMARY KEY,

                            name VARCHAR(255) NOT NULL,
                            description TEXT NULL,
                            objective TEXT NULL,

                            type VARCHAR(50) NOT NULL,
                            apply_method VARCHAR(50) NOT NULL,

                            start_at DATETIME NOT NULL,
                            end_at DATETIME NULL,

                            is_stackable BOOLEAN NOT NULL DEFAULT FALSE,
                            priority INT NOT NULL DEFAULT 0,

                            status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                            created_by BINARY(16) NULL,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                            updated_by BINARY(16) NULL,
                            updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                            version BIGINT NOT NULL DEFAULT 0,
                            is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                            INDEX idx_promotions_type (type),
                            INDEX idx_promotions_apply_method (apply_method),
                            INDEX idx_promotions_status (status),
                            INDEX idx_promotions_start_at (start_at),
                            INDEX idx_promotions_end_at (end_at),
                            INDEX idx_promotions_priority (priority),
                            INDEX idx_promotions_status_time (status, start_at, end_at),
                            INDEX idx_promotions_status_priority (status, priority),
                            INDEX idx_promotions_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 3. EVENT_PROMOTIONS
-- N-N giữa Event và Promotion
-- =========================================================

CREATE TABLE event_promotions (
                                  id BINARY(16) PRIMARY KEY,

                                  event_id BINARY(16) NOT NULL,
                                  promotion_id BINARY(16) NOT NULL,

                                  priority INT NOT NULL DEFAULT 0,
                                  is_stackable BOOLEAN NOT NULL DEFAULT FALSE,

                                  created_by BINARY(16) NULL,
                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  updated_by BINARY(16) NULL,
                                  updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                                  version BIGINT NOT NULL DEFAULT 0,
                                  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                  CONSTRAINT fk_event_promotions_event
                                      FOREIGN KEY (event_id) REFERENCES events(id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT fk_event_promotions_promotion
                                      FOREIGN KEY (promotion_id) REFERENCES promotions(id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT uk_event_promotion UNIQUE (event_id, promotion_id),

                                  INDEX idx_event_promotions_event_id (event_id),
                                  INDEX idx_event_promotions_promotion_id (promotion_id),
                                  INDEX idx_event_promotions_priority (priority),
                                  INDEX idx_event_promotions_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 4. PROMOTION_TARGETS
-- Áp dụng theo PRODUCT / CATEGORY / BRAND
-- =========================================================

CREATE TABLE promotion_targets (
                                   id BINARY(16) PRIMARY KEY,

                                   promotion_id BINARY(16) NOT NULL,

                                   target_type VARCHAR(30) NOT NULL,
                                   target_id BINARY(16) NOT NULL,

                                   is_excluded BOOLEAN NOT NULL DEFAULT FALSE,

                                   override_benefit_type VARCHAR(50) NULL,
                                   override_value DECIMAL(14, 2) NULL,
                                   override_max_discount DECIMAL(14, 2) NULL,

                                   reason TEXT NULL,

                                   created_by BINARY(16) NULL,
                                   created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   updated_by BINARY(16) NULL,
                                   updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                                   version BIGINT NOT NULL DEFAULT 0,
                                   is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                   CONSTRAINT fk_promotion_targets_promotion
                                       FOREIGN KEY (promotion_id) REFERENCES promotions(id)
                                           ON DELETE CASCADE,

                                   CONSTRAINT uk_promotion_target UNIQUE (promotion_id, target_type, target_id),

                                   INDEX idx_promotion_targets_promotion_id (promotion_id),
                                   INDEX idx_promotion_targets_target (target_type, target_id),
                                   INDEX idx_promotion_targets_excluded (is_excluded),
                                   INDEX idx_promotion_targets_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 5. PROMOTION_CONDITIONS
-- Điều kiện áp dụng promotion
-- =========================================================

CREATE TABLE promotion_conditions (
                                      id BINARY(16) PRIMARY KEY,

                                      promotion_id BINARY(16) NOT NULL,

                                      condition_type VARCHAR(50) NOT NULL,
                                      operator VARCHAR(30) NOT NULL,
                                      condition_value TEXT NOT NULL,

                                      created_by BINARY(16) NULL,
                                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                      updated_by BINARY(16) NULL,
                                      updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                                      version BIGINT NOT NULL DEFAULT 0,
                                      is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                      CONSTRAINT fk_promotion_conditions_promotion
                                          FOREIGN KEY (promotion_id) REFERENCES promotions(id)
                                              ON DELETE CASCADE,

                                      INDEX idx_promotion_conditions_promotion_id (promotion_id),
                                      INDEX idx_promotion_conditions_type (condition_type),
                                      INDEX idx_promotion_conditions_operator (operator),
                                      INDEX idx_promotion_conditions_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 6. PROMOTION_BENEFITS
-- Quyền lợi / hành động khi đủ điều kiện
-- =========================================================

CREATE TABLE promotion_benefits (
                                    id BINARY(16) PRIMARY KEY,

                                    promotion_id BINARY(16) NOT NULL,

                                    benefit_type VARCHAR(50) NOT NULL,

                                    value DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                                    max_discount DECIMAL(14, 2) NULL,

                                    gift_product_id BINARY(16) NULL,

                                    buy_quantity INT NULL,
                                    get_quantity INT NULL,

                                    reason TEXT NULL,

                                    min_margin_percent DECIMAL(5, 2) NULL,
                                    expected_margin_percent DECIMAL(5, 2) NULL,
                                    ai_recommended BOOLEAN NOT NULL DEFAULT FALSE,

                                    created_by BINARY(16) NULL,
                                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    updated_by BINARY(16) NULL,
                                    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                                    version BIGINT NOT NULL DEFAULT 0,
                                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                    CONSTRAINT fk_promotion_benefits_promotion
                                        FOREIGN KEY (promotion_id) REFERENCES promotions(id)
                                            ON DELETE CASCADE,

                                    INDEX idx_promotion_benefits_promotion_id (promotion_id),
                                    INDEX idx_promotion_benefits_type (benefit_type),
                                    INDEX idx_promotion_benefits_gift_product_id (gift_product_id),
                                    INDEX idx_promotion_benefits_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 7. COUPONS
-- PUBLIC / PERSONAL coupon
-- =========================================================

CREATE TABLE coupons (
                         id BINARY(16) PRIMARY KEY,

                         promotion_id BINARY(16) NOT NULL,

                         scope VARCHAR(30) NOT NULL,

                         customer_id BINARY(16) NULL,

                         code VARCHAR(100) NOT NULL,

                         issue_reason VARCHAR(50) NULL,
                         source_order_id BINARY(16) NULL,

                         issued_at DATETIME NULL,
                         claimed_at DATETIME NULL,

                         usage_limit INT NOT NULL DEFAULT 1,
                         used_count INT NOT NULL DEFAULT 0,

                         expires_at DATETIME NULL,

                         status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',

                         created_by BINARY(16) NULL,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         updated_by BINARY(16) NULL,
                         updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                         version BIGINT NOT NULL DEFAULT 0,
                         is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                         CONSTRAINT uk_coupons_code UNIQUE (code),

                         CONSTRAINT fk_coupons_promotion
                             FOREIGN KEY (promotion_id) REFERENCES promotions(id)
                                 ON DELETE CASCADE,

                         INDEX idx_coupons_promotion_id (promotion_id),
                         INDEX idx_coupons_scope (scope),
                         INDEX idx_coupons_customer_id (customer_id),
                         INDEX idx_coupons_status (status),
                         INDEX idx_coupons_expires_at (expires_at),
                         INDEX idx_coupons_scope_customer (scope, customer_id),
                         INDEX idx_coupons_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 8. COUPON_USAGES
-- Lịch sử giữ mã / dùng mã
-- =========================================================

CREATE TABLE coupon_usages (
                               id BINARY(16) PRIMARY KEY,

                               coupon_id BINARY(16) NOT NULL,

                               order_id BINARY(16) NOT NULL,
                               customer_id BINARY(16) NULL,

                               discount_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,

                               status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',

                               reservation_token VARCHAR(100) NULL,

                               reserved_at DATETIME NULL,
                               reservation_expires_at DATETIME NULL,

                               used_at DATETIME NULL,
                               released_at DATETIME NULL,

                               created_by BINARY(16) NULL,
                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               updated_by BINARY(16) NULL,
                               updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                               version BIGINT NOT NULL DEFAULT 0,
                               is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                               CONSTRAINT uk_coupon_usages_reservation_token UNIQUE (reservation_token),

                               CONSTRAINT fk_coupon_usages_coupon
                                   FOREIGN KEY (coupon_id) REFERENCES coupons(id)
                                       ON DELETE CASCADE,

                               INDEX idx_coupon_usages_coupon_id (coupon_id),
                               INDEX idx_coupon_usages_order_id (order_id),
                               INDEX idx_coupon_usages_customer_id (customer_id),
                               INDEX idx_coupon_usages_status (status),
                               INDEX idx_coupon_usages_used_at (used_at),
                               INDEX idx_coupon_usages_reserved_at (reserved_at),
                               INDEX idx_coupon_usages_reservation_expires_at (reservation_expires_at),
                               INDEX idx_coupon_usages_customer_status (customer_id, status),
                               INDEX idx_coupon_usages_coupon_customer (coupon_id, customer_id),
                               INDEX idx_coupon_usages_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;