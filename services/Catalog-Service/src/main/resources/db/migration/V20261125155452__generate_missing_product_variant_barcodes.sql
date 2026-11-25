INSERT INTO business_sequences (sequence_key, current_value)
VALUES ('BARCODE', 0)
ON DUPLICATE KEY UPDATE current_value = current_value;

SELECT current_value INTO @barcode_sequence
FROM business_sequences
WHERE sequence_key = 'BARCODE'
FOR UPDATE;

SET @barcode_base = CAST(GREATEST(
    COALESCE(@barcode_sequence, 0),
    COALESCE((SELECT MAX(CAST(SUBSTRING(barcode, 4, 9) AS UNSIGNED))
              FROM product_variants
              WHERE barcode REGEXP '^893[0-9]{10}$'), 0)
) AS UNSIGNED);

DROP TEMPORARY TABLE IF EXISTS tmp_missing_variant_barcodes;

CREATE TEMPORARY TABLE tmp_missing_variant_barcodes (
    id     BINARY(16) PRIMARY KEY,
    base12 CHAR(12) NOT NULL
);

INSERT INTO tmp_missing_variant_barcodes (id, base12)
SELECT id,
       CONCAT('893', LPAD(CAST(@barcode_base + ROW_NUMBER() OVER (ORDER BY created_at, id) AS UNSIGNED), 9, '0'))
FROM product_variants
WHERE is_deleted = 0
  AND (barcode IS NULL OR TRIM(barcode) = '');

UPDATE product_variants pv
JOIN tmp_missing_variant_barcodes t ON t.id = pv.id
SET pv.barcode = CONCAT(
        t.base12,
        (10 - ((
              SUBSTRING(t.base12, 1, 1) * 1 + SUBSTRING(t.base12, 2, 1) * 3
            + SUBSTRING(t.base12, 3, 1) * 1 + SUBSTRING(t.base12, 4, 1) * 3
            + SUBSTRING(t.base12, 5, 1) * 1 + SUBSTRING(t.base12, 6, 1) * 3
            + SUBSTRING(t.base12, 7, 1) * 1 + SUBSTRING(t.base12, 8, 1) * 3
            + SUBSTRING(t.base12, 9, 1) * 1 + SUBSTRING(t.base12, 10, 1) * 3
            + SUBSTRING(t.base12, 11, 1) * 1 + SUBSTRING(t.base12, 12, 1) * 3
        ) % 10)) % 10
    );

UPDATE business_sequences
SET current_value = @barcode_base + (SELECT COUNT(*) FROM tmp_missing_variant_barcodes)
WHERE sequence_key = 'BARCODE';

DROP TEMPORARY TABLE IF EXISTS tmp_missing_variant_barcodes;
