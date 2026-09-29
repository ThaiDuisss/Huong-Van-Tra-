ALTER TABLE inventory_replenishment_alerts
    ADD COLUMN open_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN alert_type = 'PRODUCTION_REQUIRED'
                  AND status IN ('OPEN', 'ACKNOWLEDGED', 'ORDERED')
             THEN 1 ELSE NULL END
    ) STORED;

ALTER TABLE inventory_replenishment_alerts
    ADD CONSTRAINT uq_inventory_alert_active_scope
        UNIQUE (inventory_item_id, location_id, alert_type, open_guard);
