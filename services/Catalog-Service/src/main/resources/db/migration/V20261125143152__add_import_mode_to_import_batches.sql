ALTER TABLE import_batches
    ADD COLUMN import_mode VARCHAR(20) NOT NULL DEFAULT 'CREATE' AFTER import_type;
