-- ============================================================
-- Migration: Lưu Product CUSTOMER, Variant & BOM từ đơn CUSTOM_BUNDLE hoàn tất.
--   1. Bổ sung recipe_sha256 (UNIQUE) trên product_variants để khử trùng công thức toàn hệ thống.
--   2. Tạo bảng inbox_events cho Kafka consumer idempotent trong Catalog-Service.
--   3. Tạo bảng customer_bundle_links liên kết order_item_id -> product/variant CUSTOMER.
--   4. Seed danh mục hệ thống CUSTOMER-BUNDLE ("Trà khách tự chọn").
-- ============================================================

ALTER TABLE product_variants
    ADD COLUMN recipe_sha256 CHAR(64) NULL AFTER composition_sha256,
    ADD CONSTRAINT uq_product_variants_recipe_sha256 UNIQUE (recipe_sha256);

CREATE TABLE IF NOT EXISTS inbox_events (
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

CREATE TABLE IF NOT EXISTS customer_bundle_links (
    id                 BINARY(16)  PRIMARY KEY,
    order_id           BINARY(16)  NOT NULL,
    order_item_id      BINARY(16)  NOT NULL,
    customer_id        BINARY(16)  NULL,
    recipe_sha256      CHAR(64)    NOT NULL,
    product_id         BINARY(16)  NOT NULL,
    product_variant_id BINARY(16)  NOT NULL,
    created_by         BINARY(16)  NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_by         BINARY(16)  NULL,
    updated_at         DATETIME(6) NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_customer_bundle_links_order_item UNIQUE (order_item_id),
    INDEX idx_customer_bundle_links_recipe (recipe_sha256),
    INDEX idx_customer_bundle_links_variant (product_variant_id)
);

INSERT INTO categories (id, category_code, name, status, version, is_deleted)
SELECT UUID_TO_BIN('6b1c2f7e-3a4d-4e5f-8a90-0c0b0d0e0f10'), 'CUSTOMER-BUNDLE', 'Trà khách tự chọn', 'ACTIVE', 0, FALSE
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE category_code = 'CUSTOMER-BUNDLE');
