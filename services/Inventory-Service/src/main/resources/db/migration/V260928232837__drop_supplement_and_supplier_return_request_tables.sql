-- Bỏ tính năng tự động tạo Yêu cầu giao bù hàng (Supplement Request) khi kiểm đếm thiếu
-- và Yêu cầu trả hàng NCC (Supplier Return Request) khi QC có hàng lỗi.
-- Xóa bảng dòng trước vì có khóa ngoại tới bảng cha.
DROP TABLE IF EXISTS inventory_supplement_request_lines;
DROP TABLE IF EXISTS inventory_supplement_requests;
DROP TABLE IF EXISTS inventory_supplier_return_request_lines;
DROP TABLE IF EXISTS inventory_supplier_return_requests;
