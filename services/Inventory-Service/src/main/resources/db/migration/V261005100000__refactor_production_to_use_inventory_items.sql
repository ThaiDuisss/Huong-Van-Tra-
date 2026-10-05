-- Refactor Production Orders & Lines to use InventoryItem references
-- This consolidates UUID fields into InventoryItem FK relationships

-- Step 1: Drop old index if exists
ALTER TABLE production_orders DROP INDEX idx_production_orders_variant;

-- Step 2: Add FK constraint to production_orders.product_variant_id
ALTER TABLE production_orders
ADD CONSTRAINT fk_production_orders_product_variant
FOREIGN KEY (product_variant_id) REFERENCES inventory_items(id);

-- Step 3: Add FK constraint to production_order_lines.ingredient_id
ALTER TABLE production_order_lines
ADD CONSTRAINT fk_production_order_lines_ingredient
FOREIGN KEY (ingredient_id) REFERENCES inventory_items(id);

-- Step 4: Rename allocation table
-- ProductionOrderMaterialAllocation -> ProductionOrderBatchAllocation
ALTER TABLE production_order_material_allocations RENAME TO production_order_batch_allocations;

-- Step 5: Recreate index on product_variant_id
ALTER TABLE production_orders
ADD INDEX idx_production_orders_variant (product_variant_id);
