CREATE TABLE files (
                       id BINARY(16) PRIMARY KEY,

                       bucket VARCHAR(100) NOT NULL,
                       object_key VARCHAR(500) NOT NULL UNIQUE,

                       original_filename VARCHAR(255) NOT NULL,
                       stored_filename VARCHAR(255) NULL,

                       content_type VARCHAR(100) NOT NULL,
                       extension VARCHAR(20) NULL,
                       size_bytes BIGINT NOT NULL DEFAULT 0,

                       checksum_sha256 VARCHAR(64) NULL,
                       etag VARCHAR(100) NULL,
                       version_id VARCHAR(100) NULL,

                       owner_id BINARY(16) NOT NULL,
                       owner_type VARCHAR(50) NOT NULL DEFAULT 'USER',

                       visibility VARCHAR(30) NOT NULL DEFAULT 'PRIVATE',
                       status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

                       created_by BINARY(16) NULL,
                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       updated_by BINARY(16) NULL,
                       updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                       version BIGINT null
                       is_deleted BOOLEAN NOT NULL DEFAULT FALSE,


                       INDEX idx_files_owner (owner_id),
                       INDEX idx_files_status (status),
                       INDEX idx_files_created_at (created_at),
                       INDEX idx_files_object_key (object_key)
);

CREATE TABLE file_references (
                                 id BINARY(16) PRIMARY KEY,
                                 file_id BINARY(16) NOT NULL,

                                 ref_type VARCHAR(50) NOT NULL,
                                 ref_id BINARY(16) NOT NULL,

                                 usage_type VARCHAR(50) NOT NULL,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 created_by BINARY(16) NULL,
                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 updated_by BINARY(16) NULL,
                                 updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
                                 version BIGINT null
                                 is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                 UNIQUE KEY uk_file_ref (file_id, ref_type, ref_id, usage_type),
                                 INDEX idx_ref (ref_type, ref_id),

                                 CONSTRAINT fk_file_references_file
                                     FOREIGN KEY (file_id) REFERENCES files(id)
);