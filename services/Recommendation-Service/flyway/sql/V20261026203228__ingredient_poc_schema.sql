-- Ingredient research only. No V1 tables, data, grants or cross-service FKs.

-- Requires MySQL >= 8.0.16 (enforced CHECK constraints).

-- Never run against a shared database without a separate approved task.

CREATE TABLE ingredient_poc_datasets (
	id VARCHAR(36) NOT NULL, 
	dataset_name VARCHAR(128) NOT NULL, 
	schema_version INTEGER NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	source_system VARCHAR(64) NOT NULL, 
	source_snapshot_hash VARCHAR(64) NOT NULL, 
	status VARCHAR(16) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	validation_policy_version VARCHAR(64) NOT NULL, 
	manifest_json JSON NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_ip_datasets_kind UNIQUE (id, source_kind), 
	CONSTRAINT uq_ip_datasets_identity UNIQUE (dataset_name, schema_version, source_snapshot_hash), 
	CONSTRAINT ck_ip_datasets_kind CHECK (source_kind IN ('SYNTHETIC','STANDARD_CURRENT_PROXY','ACTUAL')), 
	CONSTRAINT ck_ip_datasets_state CHECK (status IN ('STAGED','VALIDATED','PUBLISHED','REJECTED')), 
	CONSTRAINT ck_ip_datasets_version CHECK (schema_version > 0), 
	CONSTRAINT ck_ip_datasets_hash CHECK (length(source_snapshot_hash) = 64), 
	CONSTRAINT ck_ip_datasets_synthetic CHECK (source_kind <> 'SYNTHETIC' OR source_system LIKE 'research:%')
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE ingredient_poc_customers (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	customer_id VARCHAR(36) NOT NULL, 
	source_system VARCHAR(64) NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_ip_customers_identity UNIQUE (dataset_id, source_system, customer_id), 
	CONSTRAINT uq_ip_customers_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_customers_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_customers_dataset ON ingredient_poc_customers (dataset_id);

CREATE TABLE ingredient_poc_ingredients (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	source_system VARCHAR(64) NOT NULL, 
	source_ingredient_id VARCHAR(36), 
	ingredient_code VARCHAR(100) NOT NULL, 
	name_snapshot VARCHAR(255) NOT NULL, 
	provenance JSON NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT ck_ip_ingredient_source CHECK ((source_kind = 'SYNTHETIC' AND source_ingredient_id IS NULL AND source_system LIKE 'research:%') OR (source_kind <> 'SYNTHETIC' AND source_ingredient_id IS NOT NULL)), 
	CONSTRAINT uq_ip_ingredients_code UNIQUE (dataset_id, source_system, ingredient_code), 
	CONSTRAINT uq_ip_ingredients_source UNIQUE (dataset_id, source_system, source_ingredient_id), 
	CONSTRAINT uq_ip_ingredients_snapshot UNIQUE (id, dataset_id, ingredient_code), 
	CONSTRAINT uq_ip_ingredients_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_ingredients_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_ingredients_dataset ON ingredient_poc_ingredients (dataset_id);

CREATE TABLE ingredient_poc_products (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	source_system VARCHAR(64) NOT NULL, 
	source_product_id VARCHAR(36), 
	product_code VARCHAR(100) NOT NULL, 
	name_snapshot VARCHAR(255) NOT NULL, 
	provenance JSON NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT ck_ip_product_source CHECK ((source_kind = 'SYNTHETIC' AND source_product_id IS NULL AND source_system LIKE 'research:%') OR (source_kind <> 'SYNTHETIC' AND source_product_id IS NOT NULL)), 
	CONSTRAINT uq_ip_products_code UNIQUE (dataset_id, source_system, product_code), 
	CONSTRAINT uq_ip_products_source UNIQUE (dataset_id, source_system, source_product_id), 
	CONSTRAINT uq_ip_products_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_products_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_products_dataset ON ingredient_poc_products (dataset_id);

CREATE TABLE ingredient_poc_units (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	source_system VARCHAR(64) NOT NULL, 
	source_unit_id VARCHAR(36), 
	symbol VARCHAR(32) NOT NULL, 
	dimension VARCHAR(8) NOT NULL, 
	canonical_unit VARCHAR(8), 
	conversion_factor NUMERIC(18, 8), 
	conversion_rule_version VARCHAR(64) NOT NULL, 
	provenance JSON NOT NULL, 
	validation_status VARCHAR(16) NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT ck_ip_unit_source CHECK ((source_kind = 'SYNTHETIC' AND source_unit_id IS NULL AND source_system LIKE 'research:%') OR (source_kind <> 'SYNTHETIC' AND source_unit_id IS NOT NULL)), 
	CONSTRAINT uq_ip_units_symbol UNIQUE (dataset_id, symbol), 
	CONSTRAINT uq_ip_units_snapshot UNIQUE (id, dataset_id, symbol), 
	CONSTRAINT uq_ip_units_conversion UNIQUE (id, dataset_id, canonical_unit, conversion_factor), 
	CONSTRAINT ck_ip_units_dimension CHECK (dimension IN ('MASS','VOLUME','COUNT')), 
	CONSTRAINT ck_ip_units_state CHECK (validation_status IN ('STAGED','VALIDATED','QUARANTINED')), 
	CONSTRAINT ck_ip_units_conversion CHECK ((canonical_unit IS NULL AND conversion_factor IS NULL) OR (canonical_unit IS NOT NULL AND conversion_factor IS NOT NULL AND conversion_factor > 0 AND ((dimension = 'MASS' AND canonical_unit = 'g') OR (dimension = 'VOLUME' AND canonical_unit = 'ml') OR (dimension = 'COUNT' AND canonical_unit = 'count' AND conversion_factor = 1)))), 
	CONSTRAINT ck_ip_units_validated CHECK (validation_status <> 'VALIDATED' OR canonical_unit IS NOT NULL), 
	CONSTRAINT ck_ip_units_physical_factor CHECK (canonical_unit IS NULL OR dimension = 'COUNT' OR (dimension = 'MASS' AND ((symbol = 'g' AND conversion_factor = 1) OR (symbol = 'kg' AND conversion_factor = 1000))) OR (dimension = 'VOLUME' AND ((symbol = 'ml' AND conversion_factor = 1) OR (symbol IN ('l','L') AND conversion_factor = 1000)))), 
	CONSTRAINT uq_ip_units_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_units_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_units_dataset ON ingredient_poc_units (dataset_id);

CREATE TABLE ingredient_poc_orders (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	source_system VARCHAR(64) NOT NULL, 
	source_order_id VARCHAR(36) NOT NULL, 
	customer_id VARCHAR(36) NOT NULL, 
	order_status VARCHAR(16) NOT NULL, 
	payment_status VARCHAR(16) NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_ip_orders_business UNIQUE (dataset_id, source_system, source_order_id), 
	CONSTRAINT uq_ip_orders_customer UNIQUE (id, customer_id, dataset_id, order_status, payment_status), 
	CONSTRAINT fk_ip_orders_customer FOREIGN KEY(customer_id, dataset_id) REFERENCES ingredient_poc_customers (id, dataset_id), 
	CONSTRAINT ck_ip_orders_completed CHECK (order_status = 'COMPLETED' AND payment_status = 'PAID'), 
	CONSTRAINT uq_ip_orders_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_orders_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_orders_dataset ON ingredient_poc_orders (dataset_id);

CREATE TABLE ingredient_poc_variants (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	source_system VARCHAR(64) NOT NULL, 
	source_variant_id VARCHAR(36), 
	product_id VARCHAR(36) NOT NULL, 
	sku VARCHAR(100) NOT NULL, 
	name_snapshot VARCHAR(255) NOT NULL, 
	sale_unit_id VARCHAR(36) NOT NULL, 
	provenance JSON NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT ck_ip_variant_source CHECK ((source_kind = 'SYNTHETIC' AND source_variant_id IS NULL AND source_system LIKE 'research:%') OR (source_kind <> 'SYNTHETIC' AND source_variant_id IS NOT NULL)), 
	CONSTRAINT uq_ip_variants_sku UNIQUE (dataset_id, source_system, sku), 
	CONSTRAINT uq_ip_variants_source UNIQUE (dataset_id, source_system, source_variant_id), 
	CONSTRAINT uq_ip_variants_product UNIQUE (id, product_id, dataset_id, sale_unit_id), 
	CONSTRAINT fk_ip_variants_product FOREIGN KEY(product_id, dataset_id) REFERENCES ingredient_poc_products (id, dataset_id), 
	CONSTRAINT fk_ip_variants_unit FOREIGN KEY(sale_unit_id, dataset_id) REFERENCES ingredient_poc_units (id, dataset_id), 
	CONSTRAINT uq_ip_variants_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_variants_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_variants_dataset ON ingredient_poc_variants (dataset_id);

CREATE TABLE ingredient_poc_recipes (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	recipe_family_key VARCHAR(128) NOT NULL, 
	recipe_version INTEGER NOT NULL, 
	recipe_kind VARCHAR(24) NOT NULL, 
	base_product_id VARCHAR(36), 
	base_variant_id VARCHAR(36), 
	base_sale_unit_id VARCHAR(36), 
	portion_quantity NUMERIC(24, 8), 
	portion_unit_id VARCHAR(36), 
	portion_unit VARCHAR(32), 
	servings_per_recipe NUMERIC(24, 8), 
	recipe_hash VARCHAR(64) NOT NULL, 
	provenance JSON NOT NULL, 
	validation_status VARCHAR(16) NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_ip_recipes_version UNIQUE (dataset_id, recipe_family_key, recipe_version), 
	CONSTRAINT uq_ip_recipes_kind UNIQUE (id, dataset_id, recipe_kind), 
	CONSTRAINT fk_ip_recipes_base FOREIGN KEY(base_variant_id, base_product_id, dataset_id, base_sale_unit_id) REFERENCES ingredient_poc_variants (id, product_id, dataset_id, sale_unit_id), 
	CONSTRAINT fk_ip_recipes_portion FOREIGN KEY(portion_unit_id, dataset_id, portion_unit) REFERENCES ingredient_poc_units (id, dataset_id, symbol), 
	CONSTRAINT ck_ip_recipes_kind CHECK (recipe_kind IN ('STANDARD_CURRENT_PROXY','CUSTOM')), 
	CONSTRAINT ck_ip_recipes_origin CHECK (source_kind <> 'STANDARD_CURRENT_PROXY' OR recipe_kind = 'STANDARD_CURRENT_PROXY'), 
	CONSTRAINT ck_ip_recipes_version CHECK (recipe_version > 0), 
	CONSTRAINT ck_ip_recipes_hash CHECK (length(recipe_hash) = 64), 
	CONSTRAINT ck_ip_recipes_state CHECK (validation_status IN ('STAGED','VALIDATED','QUARANTINED')), 
	CONSTRAINT ck_ip_recipes_base CHECK ((base_product_id IS NULL AND base_variant_id IS NULL AND base_sale_unit_id IS NULL) OR (base_product_id IS NOT NULL AND base_variant_id IS NOT NULL AND base_sale_unit_id IS NOT NULL)), 
	CONSTRAINT ck_ip_recipes_standard CHECK (recipe_kind <> 'STANDARD_CURRENT_PROXY' OR base_variant_id IS NOT NULL), 
	CONSTRAINT ck_ip_recipes_portion CHECK ((portion_quantity IS NULL AND portion_unit_id IS NULL AND portion_unit IS NULL) OR (portion_quantity IS NOT NULL AND portion_quantity > 0 AND portion_unit_id IS NOT NULL AND portion_unit IS NOT NULL)), 
	CONSTRAINT ck_ip_recipes_servings CHECK (servings_per_recipe IS NULL OR servings_per_recipe > 0), 
	CONSTRAINT uq_ip_recipes_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_recipes_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_recipes_dataset ON ingredient_poc_recipes (dataset_id);

CREATE INDEX ix_ip_recipes_family ON ingredient_poc_recipes (dataset_id, recipe_family_key);

CREATE INDEX ix_ip_recipes_hash ON ingredient_poc_recipes (dataset_id, recipe_hash);

CREATE TABLE ingredient_poc_recipe_items (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	recipe_id VARCHAR(36) NOT NULL, 
	ingredient_id VARCHAR(36) NOT NULL, 
	ingredient_code_snapshot VARCHAR(100) NOT NULL, 
	ingredient_role VARCHAR(32), 
	quantity_original NUMERIC(24, 8) NOT NULL, 
	unit_id VARCHAR(36) NOT NULL, 
	unit_symbol_snapshot VARCHAR(32) NOT NULL, 
	quantity_canonical NUMERIC(24, 8), 
	canonical_unit VARCHAR(8), 
	canonical_factor NUMERIC(18, 8), 
	waste_rate NUMERIC(9, 4), 
	validation_status VARCHAR(16) NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_ip_recipe_items_ingredient UNIQUE (dataset_id, recipe_id, ingredient_id), 
	CONSTRAINT fk_ip_recipe_items_recipe FOREIGN KEY(recipe_id, dataset_id) REFERENCES ingredient_poc_recipes (id, dataset_id), 
	CONSTRAINT fk_ip_recipe_items_ingredient FOREIGN KEY(ingredient_id, dataset_id, ingredient_code_snapshot) REFERENCES ingredient_poc_ingredients (id, dataset_id, ingredient_code), 
	CONSTRAINT fk_ip_recipe_items_unit FOREIGN KEY(unit_id, dataset_id, unit_symbol_snapshot) REFERENCES ingredient_poc_units (id, dataset_id, symbol), 
	CONSTRAINT fk_ip_recipe_items_conversion FOREIGN KEY(unit_id, dataset_id, canonical_unit, canonical_factor) REFERENCES ingredient_poc_units (id, dataset_id, canonical_unit, conversion_factor), 
	CONSTRAINT ck_ip_recipe_items_quantity CHECK (quantity_original > 0), 
	CONSTRAINT ck_ip_recipe_items_state CHECK (validation_status IN ('STAGED','VALIDATED','QUARANTINED')), 
	CONSTRAINT ck_ip_recipe_items_canonical CHECK ((quantity_canonical IS NULL AND canonical_unit IS NULL AND canonical_factor IS NULL) OR (quantity_canonical IS NOT NULL AND canonical_unit IS NOT NULL AND canonical_factor IS NOT NULL AND canonical_factor > 0 AND quantity_canonical > 0 AND quantity_canonical = ROUND(quantity_original * canonical_factor, 8))), 
	CONSTRAINT ck_ip_recipe_items_validated CHECK (validation_status <> 'VALIDATED' OR quantity_canonical IS NOT NULL), 
	CONSTRAINT ck_ip_recipe_items_waste CHECK (waste_rate IS NULL OR waste_rate >= 0), 
	CONSTRAINT uq_ip_recipe_items_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_recipe_items_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_recipe_items_dataset ON ingredient_poc_recipe_items (dataset_id);

CREATE INDEX ix_ip_recipe_items_ingredient ON ingredient_poc_recipe_items (dataset_id, ingredient_id);

CREATE TABLE ingredient_poc_recipe_snapshots (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	recipe_id VARCHAR(36) NOT NULL, 
	recipe_kind VARCHAR(24) NOT NULL DEFAULT 'CUSTOM', 
	order_id VARCHAR(36) NOT NULL, 
	order_item_id VARCHAR(36) NOT NULL, 
	source_system VARCHAR(64) NOT NULL, 
	source_snapshot_id VARCHAR(128) NOT NULL, 
	observed_at DATETIME(6) NOT NULL, 
	payload_hash VARCHAR(64) NOT NULL, 
	provenance JSON NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_ip_snapshots_source UNIQUE (dataset_id, source_system, source_snapshot_id), 
	CONSTRAINT uq_ip_snapshots_line UNIQUE (dataset_id, order_id, order_item_id), 
	CONSTRAINT uq_ip_snapshots_reference UNIQUE (id, recipe_id, order_id, order_item_id, dataset_id), 
	CONSTRAINT fk_ip_snapshots_recipe FOREIGN KEY(recipe_id, dataset_id, recipe_kind) REFERENCES ingredient_poc_recipes (id, dataset_id, recipe_kind), 
	CONSTRAINT fk_ip_snapshots_order FOREIGN KEY(order_id, dataset_id) REFERENCES ingredient_poc_orders (id, dataset_id), 
	CONSTRAINT ck_ip_snapshots_actual CHECK (source_kind = 'ACTUAL'), 
	CONSTRAINT ck_ip_snapshots_recipe CHECK (recipe_kind = 'CUSTOM'), 
	CONSTRAINT ck_ip_snapshots_hash CHECK (length(payload_hash) = 64), 
	CONSTRAINT uq_ip_recipe_snapshots_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_recipe_snapshots_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_recipe_snapshots_dataset ON ingredient_poc_recipe_snapshots (dataset_id);

CREATE TABLE ingredient_poc_sale_items (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	customer_id VARCHAR(36) NOT NULL, 
	order_id VARCHAR(36) NOT NULL, 
	order_item_id VARCHAR(36) NOT NULL, 
	purchase_type VARCHAR(16) NOT NULL, 
	product_id VARCHAR(36), 
	variant_id VARCHAR(36), 
	recipe_id VARCHAR(36), 
	recipe_kind VARCHAR(24), 
	actual_recipe_snapshot_id VARCHAR(36), 
	sale_unit_id VARCHAR(36) NOT NULL, 
	quantity NUMERIC(24, 8) NOT NULL, 
	purchased_at DATETIME(6) NOT NULL, 
	order_status VARCHAR(16) NOT NULL, 
	payment_status VARCHAR(16) NOT NULL, 
	source_system VARCHAR(64) NOT NULL, 
	source_event_id VARCHAR(36), 
	source_revision INTEGER NOT NULL, 
	payload_hash VARCHAR(64) NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_ip_sales_business UNIQUE (dataset_id, order_id, order_item_id), 
	CONSTRAINT uq_ip_sales_event_line UNIQUE (dataset_id, source_system, source_event_id, order_item_id), 
	CONSTRAINT fk_ip_sales_order FOREIGN KEY(order_id, customer_id, dataset_id, order_status, payment_status) REFERENCES ingredient_poc_orders (id, customer_id, dataset_id, order_status, payment_status), 
	CONSTRAINT fk_ip_sales_variant FOREIGN KEY(variant_id, product_id, dataset_id, sale_unit_id) REFERENCES ingredient_poc_variants (id, product_id, dataset_id, sale_unit_id), 
	CONSTRAINT fk_ip_sales_recipe FOREIGN KEY(recipe_id, dataset_id, recipe_kind) REFERENCES ingredient_poc_recipes (id, dataset_id, recipe_kind), 
	CONSTRAINT fk_ip_sales_snapshot FOREIGN KEY(actual_recipe_snapshot_id, recipe_id, order_id, order_item_id, dataset_id) REFERENCES ingredient_poc_recipe_snapshots (id, recipe_id, order_id, order_item_id, dataset_id), 
	CONSTRAINT fk_ip_sales_unit FOREIGN KEY(sale_unit_id, dataset_id) REFERENCES ingredient_poc_units (id, dataset_id), 
	CONSTRAINT ck_ip_sales_type CHECK ((purchase_type = 'PRODUCT' AND product_id IS NOT NULL AND variant_id IS NOT NULL) OR (purchase_type = 'CUSTOM_BOM' AND product_id IS NULL AND variant_id IS NULL AND recipe_id IS NOT NULL AND recipe_kind = 'CUSTOM')), 
	CONSTRAINT ck_ip_sales_recipe_pair CHECK ((recipe_id IS NULL AND recipe_kind IS NULL) OR (recipe_id IS NOT NULL AND recipe_kind IS NOT NULL)), 
	CONSTRAINT ck_ip_sales_actual CHECK ((actual_recipe_snapshot_id IS NULL OR (source_kind = 'ACTUAL' AND recipe_id IS NOT NULL AND recipe_kind = 'CUSTOM')) AND (source_kind <> 'ACTUAL' OR recipe_kind IS NULL OR recipe_kind <> 'CUSTOM' OR actual_recipe_snapshot_id IS NOT NULL)), 
	CONSTRAINT ck_ip_sales_proxy CHECK (source_kind <> 'STANDARD_CURRENT_PROXY' OR purchase_type = 'PRODUCT'), 
	CONSTRAINT ck_ip_sales_quantity CHECK (quantity > 0), 
	CONSTRAINT ck_ip_sales_revision CHECK (source_revision > 0), 
	CONSTRAINT ck_ip_sales_hash CHECK (length(payload_hash) = 64), 
	CONSTRAINT uq_ip_sale_items_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_sale_items_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_sale_items_dataset ON ingredient_poc_sale_items (dataset_id);

CREATE INDEX ix_ip_sales_customer_time ON ingredient_poc_sale_items (dataset_id, customer_id, purchased_at);

CREATE INDEX ix_ip_sales_recipe_time ON ingredient_poc_sale_items (dataset_id, recipe_id, purchased_at);

CREATE TABLE ingredient_poc_feedback (
	id VARCHAR(36) NOT NULL, 
	dataset_id VARCHAR(36) NOT NULL, 
	source_kind VARCHAR(24) NOT NULL, 
	created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), 
	sale_item_id VARCHAR(36) NOT NULL, 
	source_system VARCHAR(64) NOT NULL, 
	source_feedback_id VARCHAR(128) NOT NULL, 
	feedback_type VARCHAR(24) NOT NULL, 
	rating INTEGER, 
	return_reason VARCHAR(64), 
	feedback_time DATETIME(6) NOT NULL, 
	PRIMARY KEY (id), 
	CONSTRAINT uq_ip_feedback_source UNIQUE (dataset_id, source_system, source_feedback_id), 
	CONSTRAINT fk_ip_feedback_sale FOREIGN KEY(sale_item_id, dataset_id) REFERENCES ingredient_poc_sale_items (id, dataset_id), 
	CONSTRAINT ck_ip_feedback_type CHECK (feedback_type IN ('RATING','PREFERENCE_DISLIKE','OPERATIONAL_RETURN')), 
	CONSTRAINT ck_ip_feedback_rating CHECK ((feedback_type = 'RATING' AND rating IS NOT NULL AND rating BETWEEN 1 AND 5) OR (feedback_type <> 'RATING' AND rating IS NULL)), 
	CONSTRAINT ck_ip_feedback_reason CHECK ((feedback_type <> 'OPERATIONAL_RETURN' OR (return_reason IS NOT NULL AND return_reason IN ('DEFECTIVE','DAMAGED','WRONG_ITEM','QUALITY_ISSUE','EXPIRED','OTHER'))) AND (feedback_type <> 'PREFERENCE_DISLIKE' OR return_reason IS NULL OR return_reason = 'CHANGE_OF_MIND')), 
	CONSTRAINT uq_ip_feedback_tenant UNIQUE (id, dataset_id), 
	CONSTRAINT fk_ip_feedback_dataset FOREIGN KEY(dataset_id, source_kind) REFERENCES ingredient_poc_datasets (id, source_kind)
)ENGINE=InnoDB CHARSET=utf8mb4 COLLATE utf8mb4_bin;

CREATE INDEX ix_ip_feedback_dataset ON ingredient_poc_feedback (dataset_id);

CREATE INDEX ix_ip_feedback_time ON ingredient_poc_feedback (dataset_id, feedback_time);

DELIMITER $$

CREATE TRIGGER tr_ip_datasets_insert BEFORE INSERT ON ingredient_poc_datasets
FOR EACH ROW
BEGIN
  IF NEW.status <> 'STAGED' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset must start STAGED';
  END IF;
END$$

CREATE TRIGGER tr_ip_datasets_update BEFORE UPDATE ON ingredient_poc_datasets
FOR EACH ROW
BEGIN
IF OLD.status IN ('PUBLISHED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'terminal dataset is immutable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.source_kind <=> OLD.source_kind)
     OR NOT (NEW.source_system <=> OLD.source_system)
     OR NOT (NEW.schema_version <=> OLD.schema_version)
     OR NOT (NEW.dataset_name <=> OLD.dataset_name)
     OR NOT (NEW.source_snapshot_hash <=> OLD.source_snapshot_hash)
     OR NOT (NEW.created_at <=> OLD.created_at) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset identity is immutable';
  END IF;
  IF NOT ((OLD.status = 'STAGED' AND NEW.status IN ('STAGED','VALIDATED','REJECTED'))
       OR (OLD.status = 'VALIDATED' AND NEW.status IN ('PUBLISHED','REJECTED'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'invalid dataset transition';
  END IF;
  IF OLD.status = 'VALIDATED' AND (
       NOT (NEW.manifest_json <=> OLD.manifest_json)
       OR NOT (NEW.validation_policy_version <=> OLD.validation_policy_version)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validated manifest is immutable';
  END IF;
  IF NEW.status IN ('VALIDATED','PUBLISHED') AND (
       COALESCE(JSON_TYPE(JSON_EXTRACT(NEW.manifest_json,'$.validation_complete')),'') <> 'BOOLEAN'
       OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(NEW.manifest_json,'$.validation_complete')),'false') <> 'true') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validated manifest required';
  END IF;
END$$

CREATE TRIGGER tr_ip_datasets_delete BEFORE DELETE ON ingredient_poc_datasets
FOR EACH ROW
BEGIN
IF OLD.status IN ('PUBLISHED','VALIDATED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cannot delete frozen dataset';
  END IF;
END$$

CREATE TRIGGER tr_ip_customers_insert BEFORE INSERT ON ingredient_poc_customers
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_customers_update BEFORE UPDATE ON ingredient_poc_customers
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_customers_delete BEFORE DELETE ON ingredient_poc_customers
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_ingredients_insert BEFORE INSERT ON ingredient_poc_ingredients
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_ingredients_update BEFORE UPDATE ON ingredient_poc_ingredients
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_ingredients_delete BEFORE DELETE ON ingredient_poc_ingredients
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_products_insert BEFORE INSERT ON ingredient_poc_products
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_products_update BEFORE UPDATE ON ingredient_poc_products
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_products_delete BEFORE DELETE ON ingredient_poc_products
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_units_insert BEFORE INSERT ON ingredient_poc_units
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_units_update BEFORE UPDATE ON ingredient_poc_units
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
  IF EXISTS (SELECT 1 FROM ingredient_poc_recipe_items WHERE unit_id = OLD.id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_variants WHERE sale_unit_id = OLD.id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipes WHERE portion_unit_id = OLD.id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced unit snapshot is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_units_delete BEFORE DELETE ON ingredient_poc_units
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_orders_insert BEFORE INSERT ON ingredient_poc_orders
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_orders_update BEFORE UPDATE ON ingredient_poc_orders
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_orders_delete BEFORE DELETE ON ingredient_poc_orders
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_variants_insert BEFORE INSERT ON ingredient_poc_variants
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_variants_update BEFORE UPDATE ON ingredient_poc_variants
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_variants_delete BEFORE DELETE ON ingredient_poc_variants
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_recipes_insert BEFORE INSERT ON ingredient_poc_recipes
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_recipes_update BEFORE UPDATE ON ingredient_poc_recipes
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
  IF EXISTS (SELECT 1 FROM ingredient_poc_sale_items WHERE recipe_id = OLD.id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_snapshots WHERE recipe_id = OLD.id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced recipe snapshot is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_recipes_delete BEFORE DELETE ON ingredient_poc_recipes
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF EXISTS (SELECT 1 FROM ingredient_poc_sale_items WHERE recipe_id = OLD.id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_snapshots WHERE recipe_id = OLD.id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced recipe snapshot is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_recipe_items_insert BEFORE INSERT ON ingredient_poc_recipe_items
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF EXISTS (SELECT 1 FROM ingredient_poc_sale_items WHERE recipe_id = NEW.recipe_id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_snapshots WHERE recipe_id = NEW.recipe_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced recipe snapshot is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_recipe_items_update BEFORE UPDATE ON ingredient_poc_recipe_items
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
  IF EXISTS (SELECT 1 FROM ingredient_poc_sale_items WHERE recipe_id = OLD.recipe_id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_snapshots WHERE recipe_id = OLD.recipe_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced recipe snapshot is immutable';
  END IF;
  IF EXISTS (SELECT 1 FROM ingredient_poc_sale_items WHERE recipe_id = NEW.recipe_id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_snapshots WHERE recipe_id = NEW.recipe_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced recipe snapshot is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_recipe_items_delete BEFORE DELETE ON ingredient_poc_recipe_items
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF EXISTS (SELECT 1 FROM ingredient_poc_sale_items WHERE recipe_id = OLD.recipe_id)
     OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_snapshots WHERE recipe_id = OLD.recipe_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'referenced recipe snapshot is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_recipe_snapshots_insert BEFORE INSERT ON ingredient_poc_recipe_snapshots
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NEW.recipe_id IS NOT NULL AND (
       NOT EXISTS (SELECT 1 FROM ingredient_poc_recipes
                   WHERE id = NEW.recipe_id AND validation_status = 'VALIDATED')
       OR NOT EXISTS (SELECT 1 FROM ingredient_poc_recipe_items WHERE recipe_id = NEW.recipe_id)
       OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_items
                  WHERE recipe_id = NEW.recipe_id AND validation_status <> 'VALIDATED')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validated nonempty recipe required';
  END IF;
END$$

CREATE TRIGGER tr_ip_recipe_snapshots_update BEFORE UPDATE ON ingredient_poc_recipe_snapshots
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'actual snapshot is append-only';
  IF NEW.recipe_id IS NOT NULL AND (
       NOT EXISTS (SELECT 1 FROM ingredient_poc_recipes
                   WHERE id = NEW.recipe_id AND validation_status = 'VALIDATED')
       OR NOT EXISTS (SELECT 1 FROM ingredient_poc_recipe_items WHERE recipe_id = NEW.recipe_id)
       OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_items
                  WHERE recipe_id = NEW.recipe_id AND validation_status <> 'VALIDATED')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validated nonempty recipe required';
  END IF;
END$$

CREATE TRIGGER tr_ip_recipe_snapshots_delete BEFORE DELETE ON ingredient_poc_recipe_snapshots
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_sale_items_insert BEFORE INSERT ON ingredient_poc_sale_items
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NEW.recipe_id IS NOT NULL AND (
       NOT EXISTS (SELECT 1 FROM ingredient_poc_recipes
                   WHERE id = NEW.recipe_id AND validation_status = 'VALIDATED')
       OR NOT EXISTS (SELECT 1 FROM ingredient_poc_recipe_items WHERE recipe_id = NEW.recipe_id)
       OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_items
                  WHERE recipe_id = NEW.recipe_id AND validation_status <> 'VALIDATED')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validated nonempty recipe required';
  END IF;
END$$

CREATE TRIGGER tr_ip_sale_items_update BEFORE UPDATE ON ingredient_poc_sale_items
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
  IF NEW.recipe_id IS NOT NULL AND (
       NOT EXISTS (SELECT 1 FROM ingredient_poc_recipes
                   WHERE id = NEW.recipe_id AND validation_status = 'VALIDATED')
       OR NOT EXISTS (SELECT 1 FROM ingredient_poc_recipe_items WHERE recipe_id = NEW.recipe_id)
       OR EXISTS (SELECT 1 FROM ingredient_poc_recipe_items
                  WHERE recipe_id = NEW.recipe_id AND validation_status <> 'VALIDATED')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'validated nonempty recipe required';
  END IF;
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'purchase facts are append-only; use a new dataset';
END$$

CREATE TRIGGER tr_ip_sale_items_delete BEFORE DELETE ON ingredient_poc_sale_items
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_feedback_insert BEFORE INSERT ON ingredient_poc_feedback
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

CREATE TRIGGER tr_ip_feedback_update BEFORE UPDATE ON ingredient_poc_feedback
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = NEW.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
  IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.dataset_id <=> OLD.dataset_id)
     OR NOT (NEW.source_kind <=> OLD.source_kind) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'research identity is immutable';
  END IF;
END$$

CREATE TRIGGER tr_ip_feedback_delete BEFORE DELETE ON ingredient_poc_feedback
FOR EACH ROW
BEGIN
  DECLARE parent_status VARCHAR(16);
  SELECT status INTO parent_status FROM ingredient_poc_datasets
    WHERE id = OLD.dataset_id FOR UPDATE;
  IF parent_status IS NULL OR parent_status NOT IN ('STAGED','REJECTED') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'dataset is not writable';
  END IF;
END$$

DELIMITER ;
