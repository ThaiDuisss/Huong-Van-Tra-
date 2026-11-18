-- Thu gọn scope Customer-Service (xem services/Customer-Service/plan-thu-gon-scope.md).
-- Dữ liệu sẽ được clear sau khi triển khai, nên migration này chỉ đổi cấu trúc, không chuyển dữ liệu.

-- 1. Bỏ các bảng không còn dùng: địa chỉ, tài khoản điểm tách riêng, quy tắc điểm,
--    giao dịch mua hàng và toàn bộ cơ chế xét hạng theo năm.
DROP TABLE IF EXISTS customer_tier_assignments;
DROP TABLE IF EXISTS customer_tier_yearly_metrics;
DROP TABLE IF EXISTS customer_purchase_transactions;
DROP TABLE IF EXISTS loyalty_point_rules;
DROP TABLE IF EXISTS loyalty_transactions;
DROP TABLE IF EXISTS loyalty_accounts;
DROP TABLE IF EXISTS customer_addresses;

-- 2. Khách hàng: bỏ loại khách, số dư điểm nằm ngay trên khách.
ALTER TABLE customers
    DROP COLUMN customer_type;

ALTER TABLE customers
    ADD COLUMN point_balance INT NOT NULL DEFAULT 0 AFTER total_order_count,
    ADD CONSTRAINT chk_customers_point_balance CHECK (point_balance >= 0);

-- 3. Hạng: thêm hệ số nhân điểm, giữ ngưỡng chi tiêu và số đơn.
ALTER TABLE customer_tiers
    ADD COLUMN point_multiplier DECIMAL(5, 2) NOT NULL DEFAULT 1.00 AFTER min_order_count,
    ADD CONSTRAINT chk_customer_tiers_point_multiplier CHECK (point_multiplier > 0);

-- 4. Sổ cái điểm, gắn thẳng với khách.
--    order_id: đơn gốc của EARN / REVERSE, để biết đã trừ lại bao nhiêu điểm của một đơn.
--    reference_code: mã đơn / mã phiếu hoàn để hiển thị, không hiển thị UUID.
CREATE TABLE loyalty_transactions (
    id             BINARY(16)   PRIMARY KEY,
    customer_id    BINARY(16)   NOT NULL,
    type           VARCHAR(30)  NOT NULL,
    points         INT          NOT NULL,
    balance_after  INT          NOT NULL,
    reference_type VARCHAR(30)  NOT NULL,
    reference_id   BINARY(16)   NULL,
    reference_code VARCHAR(50)  NULL,
    order_id       BINARY(16)   NULL,
    reason         VARCHAR(500) NULL,

    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by     BINARY(16)   NULL,
    updated_by     BINARY(16)   NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version        BIGINT       NULL,

    CONSTRAINT fk_loyalty_transactions_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers (id)
            ON UPDATE CASCADE
            ON DELETE CASCADE,
    -- Một chứng từ (đơn / phiếu hoàn) chỉ sinh một giao dịch mỗi loại.
    -- reference_id NULL (điều chỉnh tay) không bị ràng buộc.
    CONSTRAINT uk_loyalty_transactions_reference
        UNIQUE (type, reference_type, reference_id),
    CONSTRAINT chk_loyalty_transactions_points
        CHECK (points <> 0),
    CONSTRAINT chk_loyalty_transactions_balance
        CHECK (balance_after >= 0),

    INDEX idx_loyalty_transactions_customer_created (customer_id, created_at),
    INDEX idx_loyalty_transactions_order (order_id, type)
);

-- 5. Inbox cho consumer Kafka (idempotent theo event_id + consumer_group), cùng cấu trúc với BaseInbox.
CREATE TABLE inbox_events (
    id             BINARY(16)   PRIMARY KEY,
    event_id       VARCHAR(36)  NOT NULL,
    consumer_group VARCHAR(150) NOT NULL,
    topic          VARCHAR(150) NOT NULL,
    partition_no   INT          NULL,
    offset_no      BIGINT       NULL,
    message_key    VARCHAR(150) NULL,
    event_type     VARCHAR(100) NOT NULL,
    event_version  INT          NOT NULL DEFAULT 1,
    source_service VARCHAR(100) NULL,
    entity_type    VARCHAR(100) NULL,
    entity_id      VARCHAR(36)  NULL,
    saga_id        VARCHAR(36)  NULL,
    correlation_id VARCHAR(150) NULL,
    payload_json   LONGTEXT     NULL,
    headers_json   TEXT         NULL,
    status         VARCHAR(30)  NOT NULL,
    retry_count    INT          NOT NULL DEFAULT 0,
    max_retry      INT          NOT NULL DEFAULT 3,
    received_at    DATETIME(6)  NOT NULL,
    processed_at   DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    last_error     TEXT         NULL,

    CONSTRAINT uk_inbox_event_consumer UNIQUE (event_id, consumer_group),
    INDEX idx_inbox_status (status),
    INDEX idx_inbox_topic_offset (topic, partition_no, offset_no)
);
