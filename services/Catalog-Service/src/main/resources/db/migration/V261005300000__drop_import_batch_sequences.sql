-- Drop import_batch_sequences table from Catalog-Service database
-- This table was previously used but is no longer needed.
-- All sequence generation now uses the shared business_sequences table.

DROP TABLE IF EXISTS import_batch_sequences;
