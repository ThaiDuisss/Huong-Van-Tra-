CREATE TABLE IF NOT EXISTS order_saga (
                            id BINARY(16) NOT NULL,

                            saga_id BINARY(16) NOT NULL,
                            order_id BINARY(16) NOT NULL,
                            saga_type VARCHAR(100) NOT NULL,
                            entity_type VARCHAR(100) NOT NULL,
                            entity_id BINARY(16) NOT NULL,
                            current_step VARCHAR(100) NULL,
                            status VARCHAR(30) NOT NULL,
                            saga_data_json LONGTEXT NULL,
                            correlation_id VARCHAR(150) NULL,
                            retry_count INT NOT NULL DEFAULT 0,
                            max_retry INT NOT NULL DEFAULT 3,
                            started_at DATETIME NOT NULL,
                            completed_at DATETIME NULL,
                            failed_at DATETIME NULL,
                            updated_at DATETIME NOT NULL,
                            last_error TEXT NULL,

                            order_code VARCHAR(255) NULL,

                            PRIMARY KEY (id),
                            UNIQUE INDEX idx_order_saga_saga_id (saga_id),
                            INDEX idx_order_saga_order_id (order_id),
                            INDEX idx_order_saga_entity_id (entity_id),
                            INDEX idx_order_saga_status (status)
);

CREATE TABLE IF NOT EXISTS order_saga_step (
                                 id BINARY(16) NOT NULL,

                                 saga_id BINARY(16) NOT NULL,
                                 step_order INT NOT NULL,
                                 step_name VARCHAR(100) NOT NULL,
                                 status VARCHAR(30) NOT NULL,
                                 compensation_event_id BINARY(16) NULL,
                                 started_at DATETIME NULL,
                                 completed_at DATETIME NULL,
                                 failed_at DATETIME NULL,
                                 updated_at DATETIME NOT NULL,
                                 last_error TEXT NULL,

                                 PRIMARY KEY (id),
                                 CONSTRAINT fk_order_saga_step_saga
                                     FOREIGN KEY (saga_id) REFERENCES order_saga(saga_id)
                                         ON DELETE CASCADE,
                                 INDEX idx_order_saga_step_saga_id (saga_id),
                                 INDEX idx_order_saga_step_status (status),
                                 INDEX idx_order_saga_step_name (step_name)
);
