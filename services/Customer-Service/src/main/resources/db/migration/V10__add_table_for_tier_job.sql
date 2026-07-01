CREATE TABLE customer_purchase_transactions (
        id                BINARY(16)     PRIMARY KEY,

        customer_id       BINARY(16)     NOT NULL,
        order_id          BINARY(16)     NULL,

        source_event_id   VARCHAR(100)   NOT NULL,
        transaction_type  VARCHAR(30)    NOT NULL,
    -- PURCHASE
    -- PARTIAL_REFUND
    -- FULL_REFUND
    -- ADJUSTMENT

        amount            DECIMAL(15, 2) NOT NULL,
        order_count_delta INT            NOT NULL DEFAULT 0,

        occurred_at       DATETIME       NOT NULL,

        reason            VARCHAR(500)   NULL,
        metadata          JSON           NULL,

        is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
        created_by        VARCHAR(36)    NULL,
        updated_by        VARCHAR(36)    NULL,
        created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        version           BIGINT         NULL,

        CONSTRAINT uk_customer_purchase_transactions_event
            UNIQUE (source_event_id),

        CONSTRAINT fk_customer_purchase_transactions_customer
            FOREIGN KEY (customer_id)
                REFERENCES customers(id)
                ON UPDATE CASCADE
                ON DELETE RESTRICT,

        INDEX idx_cpt_customer_occurred
            (customer_id, occurred_at),

        INDEX idx_cpt_order
            (order_id),

        INDEX idx_cpt_occurred
            (occurred_at)
);

CREATE TABLE customer_tier_yearly_metrics (
      id                BINARY(16)     PRIMARY KEY,

      customer_id       BINARY(16)     NOT NULL,
      tier_year         SMALLINT       NOT NULL,

      total_spent       DECIMAL(15, 2) NOT NULL DEFAULT 0,
      total_order_count INT UNSIGNED   NOT NULL DEFAULT 0,

      qualified_tier_id BINARY(16)     NULL,
      calculated_at     DATETIME       NOT NULL,

      is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
      created_by        VARCHAR(36)    NULL,
      updated_by        VARCHAR(36)    NULL,
      created_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      version           BIGINT         NULL,

      CONSTRAINT uk_ctym_customer_year
          UNIQUE (customer_id, tier_year),

      CONSTRAINT fk_ctym_customer
          FOREIGN KEY (customer_id)
              REFERENCES customers(id)
              ON UPDATE CASCADE
              ON DELETE CASCADE,

      CONSTRAINT fk_ctym_qualified_tier
          FOREIGN KEY (qualified_tier_id)
              REFERENCES customer_tiers(id)
              ON UPDATE CASCADE
              ON DELETE SET NULL,

      INDEX idx_ctym_year
          (tier_year),

      INDEX idx_ctym_qualified_tier
          (qualified_tier_id),

      INDEX idx_ctym_spent
          (total_spent)
);

CREATE TABLE customer_tier_assignments (
        id                 BINARY(16)   PRIMARY KEY,

        customer_id        BINARY(16)   NOT NULL,
        tier_id            BINARY(16)   NOT NULL,

        qualification_year SMALLINT     NULL,
        assignment_reason  VARCHAR(30)  NOT NULL,

        effective_from     DATETIME     NOT NULL,
        effective_until    DATETIME     NULL,

        status             VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE',
        note               VARCHAR(500) NULL,

        is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
        created_by         VARCHAR(36)  NULL,
        updated_by         VARCHAR(36)  NULL,
        created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        version            BIGINT       NULL,

        CONSTRAINT fk_cta_customer
        FOREIGN KEY (customer_id)
           REFERENCES customers(id)
           ON UPDATE CASCADE
           ON DELETE CASCADE,

        CONSTRAINT fk_cta_tier
        FOREIGN KEY (tier_id)
           REFERENCES customer_tiers(id)
           ON UPDATE CASCADE
           ON DELETE RESTRICT,

        INDEX idx_cta_customer_status
        (customer_id, status),

        INDEX idx_cta_effective_until
        (effective_until),

        INDEX idx_cta_qualification_year
        (qualification_year)
);