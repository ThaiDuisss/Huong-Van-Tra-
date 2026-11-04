-- Thu gọn quy trình kiểm kê: 11 trạng thái -> 5 (DRAFT, COUNTING, PENDING_APPROVAL, COMPLETED, CANCELLED).
-- Dữ liệu kiểm kê cũ không cần giữ lại nên xoá sạch trước khi đổi cấu trúc.
-- Phiếu điều chỉnh (inventory_documents) đã tạo từ kiểm kê được giữ nguyên làm chứng từ.

-- 1. Xoá dữ liệu kiểm kê cũ
DELETE FROM inventory_count_attempts;
DELETE FROM inventory_count_lines;
DELETE FROM inventory_count_assignments;
DELETE FROM inventory_count_scope_locations;
DELETE FROM inventory_count_scope_items;
DELETE FROM inventory_count_scope_batches;
DELETE FROM inventory_counts;

-- 2. Phiếu kiểm kê: gộp các cột thời gian / người xử lý
ALTER TABLE inventory_counts
    DROP COLUMN frozen_at,
    DROP COLUMN reconciling_at,
    DROP COLUMN closed_at,
    DROP COLUMN closed_by,
    DROP COLUMN returned_at,
    DROP COLUMN returned_by,
    DROP COLUMN rejected_at,
    DROP COLUMN rejected_by,
    DROP COLUMN reject_reason,
    DROP COLUMN snapshot_at,
    DROP COLUMN counting_started_at,
    DROP COLUMN counting_completed_at,
    DROP COLUMN approved_by,
    DROP COLUMN approved_at;

ALTER TABLE inventory_counts
    ADD COLUMN started_at DATETIME(6) NULL AFTER supervisor_id,
    ADD COLUMN completed_at DATETIME(6) NULL AFTER submitted_at,
    ADD COLUMN completed_by BINARY(16) NULL AFTER completed_at,
    ADD COLUMN cancelled_at DATETIME(6) NULL AFTER return_reason,
    ADD COLUMN cancelled_by BINARY(16) NULL AFTER cancelled_at,
    ADD COLUMN cancel_reason VARCHAR(500) NULL AFTER cancelled_by;

CREATE INDEX idx_counts_status_started ON inventory_counts(status, started_at);

-- 3. Lượt đếm chỉ còn là lịch sử, không có trạng thái
DROP INDEX idx_count_attempts_line_status ON inventory_count_attempts;
ALTER TABLE inventory_count_attempts DROP COLUMN status;

-- 4. Phân công: ASSIGNED / DONE
ALTER TABLE inventory_count_assignments DROP COLUMN accepted_at;

-- 5. Không còn hạn mức tự duyệt
DROP TABLE inventory_count_approval_policies;
