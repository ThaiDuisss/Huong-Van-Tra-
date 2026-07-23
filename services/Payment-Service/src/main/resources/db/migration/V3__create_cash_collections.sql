CREATE TABLE cash_collections (
                                  id BINARY(16) PRIMARY KEY,

                                  payment_id BINARY(16) NOT NULL,
                                  order_id BINARY(16) NOT NULL,
                                  delivery_id BINARY(16) NULL,
                                  collector_user_id BINARY(16) NULL,

                                  amount_expected DECIMAL(14,2) NOT NULL,
                                  amount_collected DECIMAL(14,2) NULL,

                                  status VARCHAR(50) NOT NULL,

                                  assigned_at DATETIME NULL,
                                  collected_at DATETIME NULL,
                                  reconciled_at DATETIME NULL,

                                  note TEXT NULL,

                                  created_at DATETIME NOT NULL,
                                  updated_at DATETIME NULL,

                                  CONSTRAINT fk_cash_collections_payment
                                      FOREIGN KEY (payment_id) REFERENCES payments(id),

                                  CONSTRAINT uk_cash_collection_delivery_id UNIQUE (delivery_id),

                                  INDEX idx_cash_collections_payment_id (payment_id),
                                  INDEX idx_cash_collections_order_id (order_id),
                                  INDEX idx_cash_collections_status (status),
                                  INDEX idx_cash_collections_collector (collector_user_id)
);