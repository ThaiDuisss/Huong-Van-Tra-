ALTER TABLE files
    ADD COLUMN public_url VARCHAR(1000) NULL AFTER object_key,
    ADD COLUMN purpose VARCHAR(50) NOT NULL DEFAULT 'UPLOAD' AFTER size_bytes,
    ADD COLUMN ref_type VARCHAR(50) NULL AFTER owner_type,
    ADD COLUMN ref_id BINARY(16) NULL AFTER ref_type,
    ADD COLUMN completed_at DATETIME NULL AFTER created_at,
    ADD COLUMN deleted_by BINARY(16) NULL AFTER updated_at,
    ADD COLUMN deleted_at DATETIME NULL AFTER deleted_by;

ALTER TABLE files
    MODIFY COLUMN owner_id BINARY(16) NULL,
    MODIFY COLUMN owner_type VARCHAR(50) NULL,
    MODIFY COLUMN visibility VARCHAR(30) NOT NULL,
    ALTER COLUMN purpose DROP DEFAULT;

ALTER TABLE files
    DROP COLUMN stored_filename,
    DROP COLUMN checksum_sha256;

ALTER TABLE files DROP INDEX idx_files_owner;
ALTER TABLE files DROP INDEX idx_files_created_at;

CREATE INDEX idx_files_owner ON files (owner_id);
CREATE INDEX idx_files_ref ON files (ref_type, ref_id);
CREATE INDEX idx_files_visibility ON files (visibility);
CREATE INDEX idx_files_purpose ON files (purpose);
CREATE INDEX idx_files_object_key ON files (object_key);
