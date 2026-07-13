CREATE TABLE branch_delivery_configs (
                                         id BINARY(16) NOT NULL,
                                         tenant_id BINARY(16) NOT NULL,
                                         branch_id BINARY(16) NOT NULL,

                                         internal_delivery_enabled TINYINT(1) NOT NULL DEFAULT 0,
                                         max_delivery_distance_km DECIMAL(10, 2) NULL,

                                         max_orders_per_route INT NOT NULL DEFAULT 10,
                                         max_route_weight_gram INT NULL,
                                         max_route_cod_amount BIGINT NULL,
                                         max_route_duration_min INT NULL,

                                         base_shipping_fee BIGINT NOT NULL DEFAULT 0,
                                         fee_per_km BIGINT NOT NULL DEFAULT 0,

                                         fallback_provider VARCHAR(50) NULL,

                                         morning_cutoff_time TIME NULL,
                                         afternoon_cutoff_time TIME NULL,
                                         evening_cutoff_time TIME NULL,

                                         active TINYINT(1) NOT NULL DEFAULT 1,

                                         created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                         created_by VARCHAR(255) NULL,
                                         updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                         updated_by VARCHAR(255) NULL,
                                         is_deleted TINYINT(1) NOT NULL DEFAULT 0,

                                         PRIMARY KEY (id),

                                         CONSTRAINT uk_branch_delivery_configs_tenant_branch
                                             UNIQUE (tenant_id, branch_id),

                                         INDEX idx_branch_delivery_configs_active (
        tenant_id,
        active,
        is_deleted
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE shipments (
                           id BINARY(16) NOT NULL,

                           tenant_id BINARY(16) NOT NULL,

                           shipment_code VARCHAR(50) NOT NULL,

                           order_id BINARY(16) NOT NULL,
                           order_code VARCHAR(50) NOT NULL,

                           payment_id BINARY(16) NULL,
                           payment_code VARCHAR(50) NULL,

                           shipment_no INT NOT NULL DEFAULT 1,

                           delivery_mode VARCHAR(30) NOT NULL,
                           provider VARCHAR(50) NOT NULL,
                           service_code VARCHAR(100) NULL,
                           status VARCHAR(40) NOT NULL,

                           payment_type VARCHAR(30) NOT NULL,
                           cod_amount BIGINT NOT NULL DEFAULT 0,
                           cod_collected_amount BIGINT NOT NULL DEFAULT 0,

                           shipping_fee BIGINT NOT NULL DEFAULT 0,
                           actual_shipping_cost BIGINT NULL,
                           currency VARCHAR(3) NOT NULL DEFAULT 'VND',

                           customer_address_id BINARY(16) NULL,
                           recipient_name VARCHAR(255) NOT NULL,
                           recipient_phone VARCHAR(30) NOT NULL,

                           delivery_address VARCHAR(500) NOT NULL,
                           delivery_province_code VARCHAR(50) NULL,
                           delivery_district_code VARCHAR(50) NULL,
                           delivery_ward_code VARCHAR(50) NULL,
                           delivery_latitude DECIMAL(10, 7) NULL,
                           delivery_longitude DECIMAL(10, 7) NULL,

                           pickup_name_snapshot VARCHAR(255) NULL,
                           pickup_phone_snapshot VARCHAR(30) NULL,
                           pickup_address_snapshot VARCHAR(500) NOT NULL,
                           pickup_province_code VARCHAR(50) NULL,
                           pickup_district_code VARCHAR(50) NULL,
                           pickup_ward_code VARCHAR(50) NULL,
                           pickup_latitude DECIMAL(10, 7) NULL,
                           pickup_longitude DECIMAL(10, 7) NULL,

                           package_count INT NOT NULL DEFAULT 1,
                           total_weight_gram INT NOT NULL DEFAULT 0,
                           total_length_cm INT NULL,
                           total_width_cm INT NULL,
                           total_height_cm INT NULL,
                           declared_value BIGINT NOT NULL DEFAULT 0,

                           requested_from DATETIME(6) NULL,
                           requested_to DATETIME(6) NULL,
                           estimated_delivery_at DATETIME(6) NULL,
                           picked_up_at DATETIME(6) NULL,
                           delivered_at DATETIME(6) NULL,

                           courier_employee_id BINARY(16) NULL,
                           courier_name_snapshot VARCHAR(255) NULL,

                           tracking_code VARCHAR(100) NULL,
                           external_order_code VARCHAR(100) NULL,

                           failure_code VARCHAR(100) NULL,
                           failure_reason VARCHAR(1000) NULL,
                           delivery_note VARCHAR(1000) NULL,

                           version BIGINT NOT NULL DEFAULT 0,

                           created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                           created_by VARCHAR(255) NULL,
                           updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
                           updated_by VARCHAR(255) NULL,
                           is_deleted TINYINT(1) NOT NULL DEFAULT 0,

                           PRIMARY KEY (id),

                           CONSTRAINT uk_shipments_tenant_code
                               UNIQUE (tenant_id, shipment_code),

                           CONSTRAINT uk_shipments_order_number
                               UNIQUE (order_id, shipment_no),

                           INDEX idx_shipments_order (
        order_id
    ),

                           INDEX idx_shipments_payment (
        payment_id
    ),

                           INDEX idx_shipments_planning (
        tenant_id,
        branch_id,
        delivery_mode,
        status,
        requested_from
    ),

                           INDEX idx_shipments_courier_status (
        courier_employee_id,
        status
    ),

                           INDEX idx_shipments_provider_tracking (
        provider,
        tracking_code
    ),

                           INDEX idx_shipments_external_order (
        provider,
        external_order_code
    ),

                           INDEX idx_shipments_status_updated (
        status,
        updated_at
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
CREATE TABLE shipment_items (
                                id BINARY(16) NOT NULL,
                                shipment_id BINARY(16) NOT NULL,

                                product_id BINARY(16) NULL,
                                variant_id BINARY(16) NULL,

                                sku VARCHAR(100) NULL,
                                product_name VARCHAR(255) NOT NULL,
                                unit_name VARCHAR(50) NULL,

                                quantity DECIMAL(19, 3) NOT NULL,
                                weight_gram INT NULL,

                                fragile TINYINT(1) NOT NULL DEFAULT 0,
                                note VARCHAR(500) NULL,

                                created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),

                                PRIMARY KEY (id),

                                CONSTRAINT fk_shipment_items_shipment
                                    FOREIGN KEY (shipment_id)
                                        REFERENCES shipments(id)
                                        ON DELETE RESTRICT
                                        ON UPDATE RESTRICT,

                                INDEX idx_shipment_items_shipment (
        shipment_id
    ),

                                INDEX idx_shipment_items_product (
        product_id
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE delivery_proofs (
                                 id BINARY(16) NOT NULL,

                                 shipment_id BINARY(16) NOT NULL,
                                 delivery_attempt_id BINARY(16) NULL,

                                 proof_type VARCHAR(30) NOT NULL,
                                 file_id BINARY(16) NULL,

                                 otp_verified TINYINT(1) NOT NULL DEFAULT 0,
                                 receiver_name VARCHAR(255) NULL,

                                 latitude DECIMAL(10, 7) NULL,
                                 longitude DECIMAL(10, 7) NULL,

                                 captured_at DATETIME(6) NOT NULL,
                                 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                 PRIMARY KEY (id),

                                 CONSTRAINT fk_delivery_proofs_shipment
                                     FOREIGN KEY (shipment_id)
                                         REFERENCES shipments(id)
                                         ON DELETE RESTRICT
                                         ON UPDATE RESTRICT,

                                 CONSTRAINT fk_delivery_proofs_attempt
                                     FOREIGN KEY (delivery_attempt_id)
                                         REFERENCES delivery_attempts(id)
                                         ON DELETE SET NULL
                                         ON UPDATE RESTRICT,

                                 INDEX idx_delivery_proofs_shipment (
        shipment_id
    ),

                                 INDEX idx_delivery_proofs_attempt (
        delivery_attempt_id
    ),

                                 INDEX idx_delivery_proofs_file (
        file_id
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE carrier_shipments (
                                   id BINARY(16) NOT NULL,
                                   shipment_id BINARY(16) NOT NULL,

                                   provider VARCHAR(50) NOT NULL,

                                   shop_id VARCHAR(100) NULL,
                                   service_code VARCHAR(100) NULL,
                                   service_name VARCHAR(255) NULL,

                                   client_order_code VARCHAR(100) NOT NULL,
                                   external_order_code VARCHAR(100) NULL,
                                   tracking_code VARCHAR(100) NULL,

                                   provider_status VARCHAR(100) NULL,

                                   shipping_fee BIGINT NULL,
                                   insurance_fee BIGINT NULL,
                                   cod_fee BIGINT NULL,
                                   total_fee BIGINT NULL,

                                   cod_amount BIGINT NOT NULL DEFAULT 0,
                                   cod_collected TINYINT(1) NOT NULL DEFAULT 0,
                                   cod_remitted TINYINT(1) NOT NULL DEFAULT 0,
                                   cod_remitted_at DATETIME(6) NULL,

                                   expected_delivery_from DATETIME(6) NULL,
                                   expected_delivery_to DATETIME(6) NULL,

                                   create_request JSON NULL,
                                   create_response JSON NULL,

                                   created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                   updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),

                                   PRIMARY KEY (id),

                                   CONSTRAINT fk_carrier_shipments_shipment
                                       FOREIGN KEY (shipment_id)
                                           REFERENCES shipments(id)
                                           ON DELETE RESTRICT
                                           ON UPDATE RESTRICT,

                                   CONSTRAINT uk_carrier_shipments_shipment_provider
                                       UNIQUE (shipment_id, provider),

                                   CONSTRAINT uk_carrier_shipments_client_order
                                       UNIQUE (provider, client_order_code),

                                   CONSTRAINT uk_carrier_shipments_external_order
                                       UNIQUE (provider, external_order_code),

                                   INDEX idx_carrier_shipments_tracking (
        provider,
        tracking_code
    ),

                                   INDEX idx_carrier_shipments_status (
        provider,
        provider_status,
        updated_at
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE carrier_webhook_events (
                                        id BINARY(16) NOT NULL,

                                        provider VARCHAR(50) NOT NULL,

                                        provider_event_id VARCHAR(255) NULL,
                                        deduplication_key VARCHAR(255) NOT NULL,

                                        external_order_code VARCHAR(100) NULL,
                                        tracking_code VARCHAR(100) NULL,

                                        event_type VARCHAR(100) NULL,
                                        provider_status VARCHAR(100) NULL,

                                        raw_payload JSON NOT NULL,
                                        request_headers JSON NULL,

                                        status VARCHAR(30) NOT NULL,

                                        shipment_id BINARY(16) NULL,

                                        processed_at DATETIME(6) NULL,

                                        error_message VARCHAR(2000) NULL,

                                        created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                        updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),

                                        PRIMARY KEY (id),

                                        CONSTRAINT fk_carrier_webhooks_shipment
                                            FOREIGN KEY (shipment_id)
                                                REFERENCES shipments(id)
                                                ON DELETE SET NULL
                                                ON UPDATE RESTRICT,

                                        CONSTRAINT uk_carrier_webhooks_dedup
                                            UNIQUE (provider, deduplication_key),

                                        INDEX idx_carrier_webhooks_processing (
        status,
        received_at
    ),

                                        INDEX idx_carrier_webhooks_tracking (
        provider,
        tracking_code
    ),

                                        INDEX idx_carrier_webhooks_external_order (
        provider,
        external_order_code
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE shipment_status_histories (
                                           id BINARY(16) NOT NULL,
                                           shipment_id BINARY(16) NOT NULL,

                                           previous_status VARCHAR(40) NULL,
                                           new_status VARCHAR(40) NOT NULL,

                                           source VARCHAR(30) NOT NULL,
                                           source_reference VARCHAR(255) NULL,

                                           reason_code VARCHAR(100) NULL,
                                           reason VARCHAR(1000) NULL,

                                           changed_by VARCHAR(255) NULL,
                                           changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

                                           metadata JSON NULL,

                                           PRIMARY KEY (id),

                                           CONSTRAINT fk_shipment_status_histories_shipment
                                               FOREIGN KEY (shipment_id)
                                                   REFERENCES shipments(id)
                                                   ON DELETE RESTRICT
                                                   ON UPDATE RESTRICT,

                                           INDEX idx_shipment_status_histories_shipment (
        shipment_id,
        changed_at
    ),

                                           INDEX idx_shipment_status_histories_status (
        new_status,
        changed_at
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE inbox_events (
                              id BINARY(16) NOT NULL,

                              message_id VARCHAR(100) NOT NULL,
                              event_type VARCHAR(150) NOT NULL,

                              aggregate_type VARCHAR(100) NULL,
                              aggregate_id VARCHAR(100) NULL,

                              saga_id VARCHAR(100) NULL,
                              correlation_id VARCHAR(100) NULL,

                              payload JSON NOT NULL,

                              status VARCHAR(30) NOT NULL,

                              received_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                              processed_at DATETIME(6) NULL,

                              retry_count INT NOT NULL DEFAULT 0,
                              error_message VARCHAR(2000) NULL,

                              created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                              updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),

                              PRIMARY KEY (id),

                              CONSTRAINT uk_inbox_events_message
                                  UNIQUE (message_id),

                              INDEX idx_inbox_events_processing (
        status,
        received_at
    ),

                              INDEX idx_inbox_events_aggregate (
        aggregate_type,
        aggregate_id
    ),

                              INDEX idx_inbox_events_saga (
        saga_id
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


CREATE TABLE outbox_events (
                               id BINARY(16) NOT NULL,

                               event_id VARCHAR(36) NOT NULL,
                               event_type VARCHAR(150) NOT NULL,
                               event_version INT NOT NULL DEFAULT 1,

                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id VARCHAR(100) NOT NULL,

                               message_key VARCHAR(100) NOT NULL,
                               business_key VARCHAR(150) NOT NULL,

                               topic VARCHAR(255) NULL,
                               source_service VARCHAR(100) NOT NULL,

                               saga_id VARCHAR(100) NULL,
                               correlation_id VARCHAR(100) NULL,
                               causation_id VARCHAR(100) NULL,

                               payload JSON NOT NULL,
                               headers JSON NULL,

                               occurred_at DATETIME(6) NOT NULL,
                               created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                               updated_at DATETIME(6) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),

                               PRIMARY KEY (id),

                               CONSTRAINT uk_outbox_events_event
                                   UNIQUE (event_id),

                               CONSTRAINT uk_outbox_events_business
                                   UNIQUE (event_type, business_key),

                               INDEX idx_outbox_events_aggregate (
        aggregate_type,
        aggregate_id
    ),

                               INDEX idx_outbox_events_message_key (
        message_key
    ),

                               INDEX idx_outbox_events_saga (
        saga_id
    ),

                               INDEX idx_outbox_events_created_at (
        created_at
    )
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;