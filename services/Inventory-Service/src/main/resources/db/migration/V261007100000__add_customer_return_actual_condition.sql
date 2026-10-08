-- Historical posted inspections retain NULL: a declaration is not a QC observation.
-- QC must supply an actual condition before posting any unposted line.
ALTER TABLE customer_return_inspection_lines
    ADD COLUMN actual_condition VARCHAR(50) NULL;
