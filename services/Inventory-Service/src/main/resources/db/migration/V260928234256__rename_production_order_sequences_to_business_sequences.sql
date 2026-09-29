-- Đổi bảng đếm riêng của lệnh sản xuất thành bảng đếm dùng chung cho toàn Inventory-Service
-- (mỗi loại mã là 1 sequence_key, vd 'LSX' cho lệnh sản xuất). Giữ nguyên dữ liệu để mã không bị nhảy/trùng.
RENAME TABLE production_order_sequences TO business_sequences;
