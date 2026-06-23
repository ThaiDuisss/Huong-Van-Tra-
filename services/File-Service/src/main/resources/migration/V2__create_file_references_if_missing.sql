CREATE TABLE IF NOT EXISTS file_references (
                                 id BINARY(16) PRIMARY KEY,
                                 file_id BINARY(16) NOT NULL,

                                 ref_type VARCHAR(50) NOT NULL,
                                 ref_id VARCHAR(36) NOT NULL,

                                 usage_type VARCHAR(50) NOT NULL,

                                 created_by VARCHAR(255) NULL,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 updated_by VARCHAR(255) NULL,
                                 updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,

                                 version BIGINT NOT NULL DEFAULT 0,
                                 is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                 UNIQUE KEY uk_file_ref (file_id, ref_type, ref_id, usage_type),
                                 INDEX idx_ref (ref_type, ref_id),
                                 INDEX idx_file_references_file_id (file_id),

                                 CONSTRAINT fk_file_references_file
                                     FOREIGN KEY (file_id) REFERENCES files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
