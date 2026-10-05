-- Add expected_completion_date column to production_orders table
ALTER TABLE production_orders
ADD COLUMN expected_completion_date DATETIME AFTER cancel_reason;
