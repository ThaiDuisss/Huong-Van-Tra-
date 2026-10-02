-- Update any QC_PENDING documents to PENDING_EXCEPTION_APPROVAL
UPDATE inventory_documents
SET status = 'PENDING_EXCEPTION_APPROVAL'
WHERE status = 'QC_PENDING';

-- Update any QC_COMPLETED documents to POSTED
UPDATE inventory_documents
SET status = 'POSTED'
WHERE status = 'QC_COMPLETED' AND status != 'POSTED';
