-- Xử lý ngoại lệ phiếu nhập theo từng dòng: thừa (nhận hết / chỉ nhận số đặt),
-- thiếu (nhận số hiện có & đóng dòng / từ chối cả dòng); hàng không đạt QC luôn hủy.
ALTER TABLE inventory_document_lines
    ADD COLUMN excess_resolution VARCHAR(30) NULL,
    ADD COLUMN shortage_resolution VARCHAR(30) NULL,
    ADD COLUMN returned_quantity DECIMAL(18, 2) NOT NULL DEFAULT 0,
    ADD COLUMN posted_quantity DECIMAL(18, 2) NULL,
    ADD COLUMN resolution_note VARCHAR(500) NULL;

-- Người duyệt ngoại lệ, thời điểm duyệt và ghi chú duyệt (tách khỏi người ghi sổ).
ALTER TABLE inventory_documents
    ADD COLUMN exception_approved_by BINARY(16) NULL,
    ADD COLUMN exception_approved_at DATETIME NULL,
    ADD COLUMN exception_approval_note TEXT NULL;

-- Phiếu đã ghi sổ trước đây nhập kho đúng bằng số đạt QC.
UPDATE inventory_document_lines l
    JOIN inventory_documents d ON d.id = l.document_id
SET l.posted_quantity = l.accepted_quantity
WHERE d.status = 'POSTED';
