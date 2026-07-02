CREATE TABLE payments (
                          id BINARY(16) PRIMARY KEY,

                          payment_code VARCHAR(50) NOT NULL UNIQUE,
                          order_id BINARY(16) NOT NULL,
                          saga_id BINARY(16) NOT NULL,

                          method VARCHAR(30) NOT NULL,
                          provider VARCHAR(50) NOT NULL,

                          status VARCHAR(50) NOT NULL,

                          amount_required DECIMAL(14,2) NOT NULL,
                          amount_paid DECIMAL(14,2) NOT NULL DEFAULT 0.00,
                          amount_refunded DECIMAL(14,2) NOT NULL DEFAULT 0.00,
                          currency VARCHAR(10) NOT NULL DEFAULT 'VND',

                          expires_at DATETIME NULL,
                          completed_at DATETIME NULL,
                          cancelled_at DATETIME NULL,

                          version BIGINT NOT NULL DEFAULT 0,

                          created_at DATETIME NOT NULL,
                          updated_at DATETIME NULL,
                          created_by BINARY(16) NULL,
                          updated_by BINARY(16) NULL,

                          CONSTRAINT uk_payments_order UNIQUE (order_id),
                          INDEX idx_payments_saga_id (saga_id),
                          INDEX idx_payments_status (status),
                          INDEX idx_payments_payment_code (payment_code)
);

CREATE TABLE payment_attempts (
                                  id BINARY(16) PRIMARY KEY,

                                  payment_id BINARY(16) NOT NULL,
                                  attempt_no INT NOT NULL,

                                  method VARCHAR(30) NOT NULL,
                                  provider VARCHAR(50) NOT NULL,

                                  status VARCHAR(50) NOT NULL,

                                  amount_expected DECIMAL(14,2) NOT NULL,
                                  currency VARCHAR(10) NOT NULL DEFAULT 'VND',

                                  transfer_content VARCHAR(100) NULL,
                                  qr_url TEXT NULL,

                                  bank_code VARCHAR(50) NULL,
                                  bank_account_number VARCHAR(50) NULL,
                                  bank_account_name VARCHAR(255) NULL,

                                  provider_request_id VARCHAR(100) NULL,
                                  provider_reference VARCHAR(100) NULL,

                                  expires_at DATETIME NULL,
                                  completed_at DATETIME NULL,

                                  metadata JSON NULL,

                                  created_at DATETIME NOT NULL,
                                  updated_at DATETIME NULL,

                                  CONSTRAINT fk_payment_attempts_payment
                                      FOREIGN KEY (payment_id) REFERENCES payments(id),

                                  CONSTRAINT uk_payment_attempt_no UNIQUE (payment_id, attempt_no),
                                  CONSTRAINT uk_payment_attempt_transfer_content UNIQUE (transfer_content),

                                  INDEX idx_payment_attempts_payment_id (payment_id),
                                  INDEX idx_payment_attempts_status (status)
);

CREATE TABLE payment_transactions (
                                      id BINARY(16) PRIMARY KEY,

                                      payment_id BINARY(16) NOT NULL,
                                      attempt_id BINARY(16) NULL,

                                      transaction_type VARCHAR(30) NOT NULL,
                                      method VARCHAR(30) NOT NULL,
                                      provider VARCHAR(50) NOT NULL,

                                      status VARCHAR(50) NOT NULL,

                                      amount DECIMAL(14,2) NOT NULL,
                                      currency VARCHAR(10) NOT NULL DEFAULT 'VND',

                                      external_transaction_id VARCHAR(100) NULL,
                                      reference_code VARCHAR(100) NULL,

                                      gateway VARCHAR(100) NULL,
                                      bank_account_number VARCHAR(50) NULL,
                                      counter_account_number VARCHAR(50) NULL,
                                      counter_account_name VARCHAR(255) NULL,

                                      transfer_content TEXT NULL,

                                      paid_at DATETIME NULL,
                                      raw_payload JSON NULL,

                                      created_at DATETIME NOT NULL,
                                      updated_at DATETIME NULL,

                                      CONSTRAINT fk_payment_transactions_payment
                                          FOREIGN KEY (payment_id) REFERENCES payments(id),

                                      CONSTRAINT fk_payment_transactions_attempt
                                          FOREIGN KEY (attempt_id) REFERENCES payment_attempts(id),

                                      CONSTRAINT uk_provider_external_txn UNIQUE (provider, external_transaction_id),

                                      INDEX idx_payment_transactions_payment_id (payment_id),
                                      INDEX idx_payment_transactions_status (status),
                                      INDEX idx_payment_transactions_reference_code (reference_code)
);

CREATE TABLE payment_webhook_events (
                                        id BINARY(16) PRIMARY KEY,

                                        provider VARCHAR(50) NOT NULL,

                                        provider_event_id VARCHAR(100) NOT NULL,
                                        reference_code VARCHAR(100) NULL,

                                        payment_code VARCHAR(50) NULL,
                                        matched_payment_id BINARY(16) NULL,

                                        status VARCHAR(50) NOT NULL,

                                        transfer_type VARCHAR(30) NULL,
                                        transfer_amount DECIMAL(14,2) NULL,
                                        transaction_date DATETIME NULL,

                                        raw_payload JSON NOT NULL,

                                        processed_at DATETIME NULL,
                                        error_message TEXT NULL,

                                        created_at DATETIME NOT NULL,
                                        updated_at DATETIME NULL,

                                        CONSTRAINT uk_webhook_provider_event UNIQUE (provider, provider_event_id),

                                        INDEX idx_webhook_payment_code (payment_code),
                                        INDEX idx_webhook_status (status),
                                        INDEX idx_webhook_matched_payment_id (matched_payment_id)
);

CREATE TABLE outbox_events (
                               id BINARY(16) PRIMARY KEY,

                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id BINARY(16) NOT NULL,

                               event_type VARCHAR(150) NOT NULL,
                               event_version INT NOT NULL DEFAULT 1,

                               payload JSON NOT NULL,
                               headers JSON NULL,

                               saga_id BINARY(16) NULL,
                               correlation_id VARCHAR(100) NULL,
                               causation_id VARCHAR(100) NULL,

                               status VARCHAR(30) NOT NULL,
                               retry_count INT NOT NULL DEFAULT 0,

                               available_at DATETIME NOT NULL,
                               published_at DATETIME NULL,
                               last_error TEXT NULL,

                               created_at DATETIME NOT NULL,
                               updated_at DATETIME NULL,

                               INDEX idx_outbox_status_available (status, available_at, created_at),
                               INDEX idx_outbox_aggregate (aggregate_type, aggregate_id),
                               INDEX idx_outbox_saga_id (saga_id)
);

CREATE TABLE inbox_events (
                              id BINARY(16) PRIMARY KEY,

                              message_id VARCHAR(100) NOT NULL UNIQUE,

                              source_service VARCHAR(100) NOT NULL,
                              event_type VARCHAR(150) NOT NULL,

                              aggregate_type VARCHAR(100) NULL,
                              aggregate_id BINARY(16) NULL,

                              saga_id BINARY(16) NULL,
                              correlation_id VARCHAR(100) NULL,

                              status VARCHAR(30) NOT NULL,

                              processed_at DATETIME NULL,
                              error_message TEXT NULL,

                              created_at DATETIME NOT NULL,
                              updated_at DATETIME NULL,

                              INDEX idx_inbox_saga_id (saga_id),
                              INDEX idx_inbox_status (status)
);