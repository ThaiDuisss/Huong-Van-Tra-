-- 1. CUSTOMER_TIERS
CREATE TABLE customer_tiers (
    id            BINARY(16)     PRIMARY KEY,
    code          VARCHAR(50)    NOT NULL UNIQUE,
    name          VARCHAR(100)   NOT NULL,
    min_spending  DECIMAL(15, 2) NOT NULL DEFAULT 0,
    discount_rate DECIMAL(5, 2)  NOT NULL DEFAULT 0,
    status        VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',

    is_deleted    BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by_id VARCHAR(36)    NULL,
    updated_by_id VARCHAR(36)    NULL,
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       BIGINT         NULL
);

-- 2. CUSTOMERS
CREATE TABLE customers (
    id            BINARY(16)       PRIMARY KEY,
    customer_code VARCHAR(50)    NOT NULL UNIQUE,
    full_name     VARCHAR(150)   NOT NULL,
    phone         VARCHAR(20)    NOT NULL UNIQUE,
    email         VARCHAR(150)   NULL UNIQUE,
    date_of_birth DATE           NULL,
    tier_id       BINARY(16)       NULL,
    credit_limit  DECIMAL(15, 2) NOT NULL DEFAULT 0,
    status        VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',

    is_deleted    BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by_id VARCHAR(36)    NULL,
    updated_by_id VARCHAR(36)    NULL,
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       BIGINT         NULL,

    CONSTRAINT fk_customers_tier
        FOREIGN KEY (tier_id)
            REFERENCES customer_tiers(id)
            ON UPDATE CASCADE
            ON DELETE SET NULL
);

-- 3. CUSTOMER_ADDRESSES
CREATE TABLE customer_addresses (
    id              BINARY(16)     PRIMARY KEY,
    customer_id     BINARY(16)     NOT NULL,
    recipient_name  VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(20)  NOT NULL,
    address_line    VARCHAR(255) NOT NULL,
    province        VARCHAR(100) NOT NULL,
    district        VARCHAR(100) NOT NULL,
    ward            VARCHAR(100) NOT NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,

    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by_id   VARCHAR(36)  NULL,
    updated_by_id   VARCHAR(36)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         BIGINT       NULL,

    CONSTRAINT fk_customer_addresses_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers(id)
            ON UPDATE CASCADE
            ON DELETE CASCADE
);

-- 4. LOYALTY_ACCOUNTS
CREATE TABLE loyalty_accounts (
    id            BINARY(16) PRIMARY KEY,
    customer_id   BINARY(16) NOT NULL UNIQUE,
    point_balance INT      NOT NULL DEFAULT 0,

    is_deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by_id VARCHAR(36) NULL,
    updated_by_id VARCHAR(36) NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       BIGINT      NULL,

    CONSTRAINT fk_loyalty_accounts_customer
        FOREIGN KEY (customer_id)
            REFERENCES customers(id)
            ON UPDATE CASCADE
            ON DELETE CASCADE
);

-- 5. LOYALTY_TRANSACTIONS
CREATE TABLE loyalty_transactions (
    id                 BINARY(16)    PRIMARY KEY,
    loyalty_account_id BINARY(16)    NOT NULL,
    type               VARCHAR(30) NOT NULL,
    points             INT         NOT NULL,
    reference_type     VARCHAR(50) NULL,
    reference_id       BINARY(16)    NULL,

    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by_id      VARCHAR(36) NULL,
    updated_by_id      VARCHAR(36) NULL,
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version            BIGINT      NULL,

    CONSTRAINT fk_loyalty_transactions_account
        FOREIGN KEY (loyalty_account_id)
            REFERENCES loyalty_accounts(id)
            ON UPDATE CASCADE
            ON DELETE CASCADE,

    CONSTRAINT chk_loyalty_transactions_points
        CHECK (points <> 0)
);


-- INDEXES
CREATE INDEX idx_customer_tiers_status
    ON customer_tiers(status);

CREATE INDEX idx_customers_full_name
    ON customers(full_name);

CREATE INDEX idx_customers_status
    ON customers(status);

CREATE INDEX idx_customers_tier_id
    ON customers(tier_id);

CREATE INDEX idx_customer_addresses_customer_id
    ON customer_addresses(customer_id);

CREATE INDEX idx_customer_addresses_is_default
    ON customer_addresses(is_default);

CREATE INDEX idx_loyalty_transactions_account_id
    ON loyalty_transactions(loyalty_account_id);

CREATE INDEX idx_loyalty_transactions_reference
    ON loyalty_transactions(reference_type, reference_id);

CREATE INDEX idx_loyalty_transactions_created_at
    ON loyalty_transactions(created_at);