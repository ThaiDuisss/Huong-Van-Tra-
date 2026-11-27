-- Một phiếu QC hoàn hàng có thể có nhiều phiếu tiêu hủy: phiếu WRITE_OFF cũ
-- (CUSTOMER_RETURN_INSPECTION) và phiếu tiêu hủy hàng lỗi DAMAGED
-- (CUSTOMER_RETURN_DAMAGED). Thay unique key bằng index thường; index mới
-- phải tạo trước khi drop unique key vì FK fk_disposal_source_inspection cần nó.

ALTER TABLE disposal_requests
    ADD INDEX idx_disposal_requests_source_inspection (source_inspection_id);

ALTER TABLE disposal_requests
    DROP INDEX uq_disposal_source_inspection;
