-- Reuse file_references. NULL generated keys leave all other reference types unchanged.
ALTER TABLE file_references
    ADD COLUMN ai_report_ref_id BINARY(16)
        GENERATED ALWAYS AS (CASE WHEN ref_type = 'AI_REPORT' AND usage_type = 'ORIGINAL'
            AND is_deleted = FALSE THEN ref_id ELSE NULL END) STORED,
    ADD COLUMN ai_report_file_id BINARY(16)
        GENERATED ALWAYS AS (CASE WHEN ref_type = 'AI_REPORT' AND usage_type = 'ORIGINAL'
            AND is_deleted = FALSE THEN file_id ELSE NULL END) STORED,
    ADD CONSTRAINT uq_ai_report_original_report UNIQUE (ai_report_ref_id),
    ADD CONSTRAINT uq_ai_report_original_file UNIQUE (ai_report_file_id);
