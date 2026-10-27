-- Nguyên liệu không theo dõi hạn sử dụng: xóa HSD đã khai báo trên các lô nguyên liệu.
UPDATE inventory_batches b
    JOIN inventory_items i ON i.id = b.inventory_item_id
SET b.expiry_date = NULL
WHERE i.item_type = 'INGREDIENT'
  AND b.expiry_date IS NOT NULL;
