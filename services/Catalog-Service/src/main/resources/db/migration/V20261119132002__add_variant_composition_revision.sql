ALTER TABLE product_variants
    ADD COLUMN composition_revision BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN composition_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT ck_pv_composition_basis CHECK (
        (composition_revision = 0 AND composition_sha256 IS NULL)
        OR (composition_revision > 0 AND composition_sha256 IS NOT NULL)
    );
