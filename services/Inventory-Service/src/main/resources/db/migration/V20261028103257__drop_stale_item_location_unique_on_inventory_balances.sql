-- Tồn kho lưu theo (batch, vị trí) qua uq_inventory_balances_batch_loc. Một số DB còn unique (item, vị trí)
-- do Hibernate tự sinh trước đây (tên UK...), làm hỏng việc đưa batch thứ hai của cùng mặt hàng lên cùng kệ.
SET @stale_unique_name := (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'inventory_balances'
      AND non_unique = 0
      AND index_name <> 'PRIMARY'
    GROUP BY index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') = 'inventory_item_id,location_id'
    LIMIT 1
);

SET @drop_stale_unique_sql := IF(
    @stale_unique_name IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE inventory_balances DROP INDEX `', @stale_unique_name, '`')
);

PREPARE drop_stale_unique_stmt FROM @drop_stale_unique_sql;
EXECUTE drop_stale_unique_stmt;
DEALLOCATE PREPARE drop_stale_unique_stmt;
