-- Run only after verifying the deployment database and its Flyway history.
-- No USE/schema qualification: the deployment's explicit Flyway target owns this DDL.
CREATE TABLE ai_reports (
    id CHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    version VARCHAR(50) NOT NULL,
    file_id CHAR(36) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    created_by CHAR(36) NOT NULL,
    updated_by CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_ai_reports_file_id UNIQUE (file_id),
    INDEX idx_ai_reports_updated_id (updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Durable saga intent covers bind-success/local-commit-failure without duplicating files.
CREATE TABLE ai_report_imports (
    id CHAR(36) NOT NULL PRIMARY KEY,
    file_id CHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    version VARCHAR(50) NOT NULL,
    created_by CHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_error VARCHAR(50) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_ai_report_imports_file_actor UNIQUE (file_id, created_by),
    INDEX idx_ai_report_imports_status_time (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
