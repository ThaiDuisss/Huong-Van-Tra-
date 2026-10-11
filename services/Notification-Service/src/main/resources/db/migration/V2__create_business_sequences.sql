-- Bảng bộ đếm dùng chung cho BusinessSequenceService (common_core): mỗi loại mã là một sequence_key.
CREATE TABLE IF NOT EXISTS business_sequences (
    sequence_key  VARCHAR(50) NOT NULL PRIMARY KEY,
    current_value BIGINT      NOT NULL
);
