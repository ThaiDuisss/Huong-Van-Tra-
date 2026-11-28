ALTER TABLE inventory_replenishment_policies
    DROP COLUMN safety_stock,
    DROP COLUMN reorder_point,
    DROP COLUMN standard_reorder_quantity,
    DROP COLUMN supplier_lead_time_days,
    DROP COLUMN average_daily_consumption;
