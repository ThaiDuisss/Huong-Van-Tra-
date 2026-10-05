-- Refactor Production Order workflow to simplified 3-status system
-- - Remove RELEASED, IN_PROGRESS, COMPLETED statuses
-- - Add REJECTED status
-- - Update ProductionOrder entity fields

-- Step 1: Drop old index
ALTER TABLE production_orders DROP INDEX idx_production_orders_variant;

-- Step 2: Drop unused columns from simplified workflow
ALTER TABLE production_orders
DROP COLUMN actual_good_quantity,
DROP COLUMN actual_rejected_quantity,
DROP COLUMN finished_batch_id,
DROP COLUMN released_by,
DROP COLUMN released_at,
DROP COLUMN started_by,
DROP COLUMN started_at,
DROP COLUMN completed_by,
DROP COLUMN completed_at;

-- Step 3: Add new columns for rejection
ALTER TABLE production_orders
ADD COLUMN rejected_by BINARY(16) AFTER approved_at,
ADD COLUMN rejected_at DATETIME AFTER rejected_by;

-- Step 4: Add FK constraint to production_orders.product_variant_id
ALTER TABLE production_orders
ADD CONSTRAINT fk_production_orders_product_variant
FOREIGN KEY (product_variant_id) REFERENCES inventory_items(id);

-- Step 5: Add FK constraint to production_order_lines.ingredient_id
ALTER TABLE production_order_lines
ADD CONSTRAINT fk_production_order_lines_ingredient
FOREIGN KEY (ingredient_id) REFERENCES inventory_items(id);

-- Step 6: Rename allocation table
-- ProductionOrderMaterialAllocation -> ProductionOrderBatchAllocation
ALTER TABLE production_order_material_allocations RENAME TO production_order_batch_allocations;

-- Step 7: Recreate index on product_variant_id
ALTER TABLE production_orders
ADD INDEX idx_production_orders_variant (product_variant_id);
