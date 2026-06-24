-- Migration V6: Add SKU sequence
INSERT INTO business_sequences (sequence_key, current_value)
SELECT 'SKU', COUNT(*) FROM product_variants;
