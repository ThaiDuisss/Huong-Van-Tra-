CREATE TABLE events (
                        id BINARY(16) PRIMARY KEY,

                        name VARCHAR(255) NOT NULL,
                        description TEXT NULL,

                        start_at DATETIME NOT NULL,
                        end_at DATETIME NOT NULL,

                        status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                        version BIGINT NOT NULL DEFAULT 0,
                        is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                        INDEX idx_events_status (status),
                        INDEX idx_events_start_at (start_at),
                        INDEX idx_events_end_at (end_at),
                        INDEX idx_events_status_time (status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE promotions (
                            id BINARY(16) PRIMARY KEY,

                            event_id BINARY(16) NULL,

                            name VARCHAR(255) NOT NULL,
                            type VARCHAR(50) NOT NULL,
                            apply_method VARCHAR(50) NOT NULL,

                            start_at DATETIME NOT NULL,
                            end_at DATETIME NOT NULL,

                            is_stackable BOOLEAN NOT NULL DEFAULT FALSE,
                            priority INT NOT NULL DEFAULT 0,
                            status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                            version BIGINT NOT NULL DEFAULT 0,
                            is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                            CONSTRAINT fk_promotions_event
                                FOREIGN KEY (event_id) REFERENCES events(id)
                                    ON DELETE SET NULL,

                            INDEX idx_promotions_event_id (event_id),
                            INDEX idx_promotions_type (type),
                            INDEX idx_promotions_apply_method (apply_method),
                            INDEX idx_promotions_status (status),
                            INDEX idx_promotions_start_at (start_at),
                            INDEX idx_promotions_end_at (end_at),
                            INDEX idx_promotions_priority (priority),
                            INDEX idx_promotions_status_time (status, start_at, end_at),
                            INDEX idx_promotions_status_priority (status, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE promotion_conditions (
                                      id BINARY(16) PRIMARY KEY,

                                      promotion_id BINARY(16) NOT NULL,

                                      condition_type VARCHAR(50) NOT NULL,
                                      operator VARCHAR(30) NOT NULL,
                                      condition_value VARCHAR(255) NOT NULL,

                                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                                      version BIGINT NOT NULL DEFAULT 0,
                                      is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                      CONSTRAINT fk_promotion_conditions_promotion
                                          FOREIGN KEY (promotion_id) REFERENCES promotions(id)
                                              ON DELETE CASCADE,

                                      INDEX idx_promotion_conditions_promotion_id (promotion_id),
                                      INDEX idx_promotion_conditions_type (condition_type),
                                      INDEX idx_promotion_conditions_promotion_type (promotion_id, condition_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE promotion_benefits (
                                    id BINARY(16) PRIMARY KEY,

                                    promotion_id BINARY(16) NOT NULL,

                                    benefit_type VARCHAR(50) NOT NULL,
                                    value DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                                    max_discount DECIMAL(14, 2) NULL,

                                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                                    version BIGINT NOT NULL DEFAULT 0,
                                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                    CONSTRAINT fk_promotion_benefits_promotion
                                        FOREIGN KEY (promotion_id) REFERENCES promotions(id)
                                            ON DELETE CASCADE,

                                    INDEX idx_promotion_benefits_promotion_id (promotion_id),
                                    INDEX idx_promotion_benefits_type (benefit_type),
                                    INDEX idx_promotion_benefits_promotion_type (promotion_id, benefit_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE coupons (
                         id BINARY(16) PRIMARY KEY,

                         promotion_id BINARY(16) NOT NULL,

    /*
      customer_id là ID của customer-service.
      Không tạo FK tới bảng customers vì promotion-service không own customer table.
      NULL nghĩa là coupon dùng chung cho mọi khách.
      NOT NULL nghĩa là coupon được gán riêng cho một khách.
    */
                         customer_id BINARY(16) NULL,

                         code VARCHAR(100) NOT NULL,

                         usage_limit INT NOT NULL DEFAULT 1,
                         used_count INT NOT NULL DEFAULT 0,

                         expires_at DATETIME NULL,
                         status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                         version BIGINT NOT NULL DEFAULT 0,
                         is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                         CONSTRAINT uk_coupons_code UNIQUE (code),

                         CONSTRAINT fk_coupons_promotion
                             FOREIGN KEY (promotion_id) REFERENCES promotions(id)
                                 ON DELETE CASCADE,

                         INDEX idx_coupons_promotion_id (promotion_id),
                         INDEX idx_coupons_customer_id (customer_id),
                         INDEX idx_coupons_status (status),
                         INDEX idx_coupons_expires_at (expires_at),
                         INDEX idx_coupons_promotion_status (promotion_id, status),
                         INDEX idx_coupons_customer_status (customer_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE coupon_usages (
                               id BINARY(16) PRIMARY KEY,

                               coupon_id BINARY(16) NOT NULL,

    /*
      order_id là ID của order-service.
      customer_id là ID của customer-service.
      Không tạo FK tới orders/customers vì khác bounded context.
    */
                               order_id BINARY(16) NOT NULL,
                               customer_id BINARY(16) NOT NULL,

                               discount_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,

    /*
      Flow gợi ý:
      RESERVED -> APPLIED
      RESERVED -> RELEASED
      RESERVED -> EXPIRED
      APPLIED  -> CANCELLED nếu order bị hủy sau thanh toán
    */
                               status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',

                               reservation_token VARCHAR(100) NULL,
                               reserved_at DATETIME NULL,
                               reservation_expires_at DATETIME NULL,

                               used_at DATETIME NULL,
                               released_at DATETIME NULL,

                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                               version BIGINT NOT NULL DEFAULT 0,
                               is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                               CONSTRAINT uk_coupon_usages_coupon_order UNIQUE (coupon_id, order_id),
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
                               INDEX idx_coupon_usages_coupon_customer (coupon_id, customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;