CREATE TABLE payments (
                          id CHAR(36) PRIMARY KEY,

                          payment_code VARCHAR(50) NOT NULL UNIQUE,

                          order_id CHAR(36) NOT NULL,

                          amount_required DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                          amount_paid DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                          amount_refunded DECIMAL(14, 2) NOT NULL DEFAULT 0.00,

                          status VARCHAR(50) NOT NULL,

                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          completed_at DATETIME NULL,

                          created_by CHAR(36) NULL,
                          updated_by CHAR(36) NULL,
                          updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                          version BIGINT NULL,
                          is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                          INDEX idx_payments_payment_code (payment_code),
                          INDEX idx_payments_order_id (order_id),
                          INDEX idx_payments_status (status),
                          INDEX idx_payments_created_at (created_at),
                          INDEX idx_payments_completed_at (completed_at)
);

CREATE TABLE payment_transactions (
                                      id CHAR(36) PRIMARY KEY,

                                      payment_id CHAR(36) NOT NULL,

                                      method VARCHAR(50) NOT NULL,
                                      amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                                      status VARCHAR(50) NOT NULL,

                                      provider VARCHAR(100) NULL,
                                      external_transaction_id VARCHAR(255) NULL,

                                      paid_at DATETIME NULL,

                                      created_by CHAR(36) NULL,
                                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_by CHAR(36) NULL,
                                      updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                                      version BIGINT NULL,
                                      is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                      CONSTRAINT fk_payment_transactions_payment
                                          FOREIGN KEY (payment_id) REFERENCES payments(id)
                                              ON DELETE CASCADE,

                                      INDEX idx_payment_transactions_payment_id (payment_id),
                                      INDEX idx_payment_transactions_method (method),
                                      INDEX idx_payment_transactions_status (status),
                                      INDEX idx_payment_transactions_provider (provider),
                                      INDEX idx_payment_transactions_paid_at (paid_at)
);

CREATE TABLE refunds (
                         id CHAR(36) PRIMARY KEY,

                         payment_id CHAR(36) NOT NULL,
                         return_request_id CHAR(36) NULL,

                         amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
                         method VARCHAR(50) NOT NULL,
                         status VARCHAR(50) NOT NULL,

                         external_refund_id VARCHAR(255) NULL,
                         reason VARCHAR(255) NULL,

                         created_by CHAR(36) NULL,
                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_by CHAR(36) NULL,
                         updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                         version BIGINT NULL,
                         is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                         CONSTRAINT fk_refunds_payment
                             FOREIGN KEY (payment_id) REFERENCES payments(id)
                                 ON DELETE CASCADE,

                         INDEX idx_refunds_payment_id (payment_id),
                         INDEX idx_refunds_return_request_id (return_request_id),
                         INDEX idx_refunds_status (status),
                         INDEX idx_refunds_external_refund_id (external_refund_id)
);

CREATE TABLE payment_links (
                               id CHAR(36) PRIMARY KEY,

                               payment_id CHAR(36) NOT NULL,

                               provider VARCHAR(100) NOT NULL,
                               checkout_url TEXT NULL,
                               qr_code TEXT NULL,

                               expires_at DATETIME NULL,
                               status VARCHAR(50) NOT NULL,

                               created_by CHAR(36) NULL,
                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_by CHAR(36) NULL,
                               updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                               version BIGINT NULL,
                               is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                               CONSTRAINT fk_payment_links_payment
                                   FOREIGN KEY (payment_id) REFERENCES payments(id)
                                       ON DELETE CASCADE,

                               INDEX idx_payment_links_payment_id (payment_id),
                               INDEX idx_payment_links_provider (provider),
                               INDEX idx_payment_links_status (status),
                               INDEX idx_payment_links_expires_at (expires_at)
);

CREATE TABLE payment_webhooks (
                                  id CHAR(36) PRIMARY KEY,

                                  provider VARCHAR(100) NOT NULL,
                                  external_event_id VARCHAR(255) NULL,

                                  payload_json JSON NULL,
                                  processing_status VARCHAR(50) NOT NULL,

                                  received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  created_by CHAR(36) NULL,
                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_by CHAR(36) NULL,
                                  updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                                  version BIGINT NULL,
                                  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                  INDEX idx_payment_webhooks_provider (provider),
                                  INDEX idx_payment_webhooks_external_event_id (external_event_id),
                                  INDEX idx_payment_webhooks_processing_status (processing_status),
                                  INDEX idx_payment_webhooks_received_at (received_at)
);