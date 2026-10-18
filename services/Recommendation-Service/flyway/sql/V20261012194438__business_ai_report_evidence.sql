CREATE TABLE recommendation_snapshots (
 id VARCHAR(36) PRIMARY KEY, customer_id VARCHAR(36) NOT NULL, request_key VARCHAR(36) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, generated_at DATETIME(6) NOT NULL, observed_at DATETIME(6) NOT NULL,
 mode VARCHAR(20) NOT NULL, source VARCHAR(20) NOT NULL, fallback_reason VARCHAR(100) NULL,
 evaluation_days INT NOT NULL, context JSON NOT NULL, response JSON NOT NULL,
 CONSTRAINT uq_snapshot_customer_request UNIQUE(customer_id, request_key),
 INDEX idx_snapshot_customer_time(customer_id, generated_at), INDEX idx_snapshot_generated(generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE report_purchase_orders (
 id VARCHAR(36) PRIMARY KEY, source_event_id VARCHAR(36) NOT NULL, customer_id VARCHAR(36) NULL,
 occurred_at DATETIME(6) NOT NULL, observed_at DATETIME(6) NOT NULL, evidence_hash VARCHAR(64) NOT NULL,
 `lines` JSON NOT NULL, INDEX idx_report_order_customer_time(customer_id, occurred_at), INDEX idx_report_order_time(occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE business_report_exports (
 id VARCHAR(36) PRIMARY KEY, created_by VARCHAR(36) NOT NULL, request_key VARCHAR(36) NOT NULL,
 from_date DATE NOT NULL, to_date DATE NOT NULL, generated_at DATETIME(6) NOT NULL, status VARCHAR(20) NOT NULL,
 file_id VARCHAR(36) NULL, file_name VARCHAR(255) NOT NULL, sha256 VARCHAR(64) NULL, workbook LONGBLOB NULL,
 evidence JSON NULL, last_error VARCHAR(40) NULL,
 CONSTRAINT uq_business_export_actor_request UNIQUE(created_by, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
