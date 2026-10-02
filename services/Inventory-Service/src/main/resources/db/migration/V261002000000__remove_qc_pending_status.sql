-- Migration: Remove QC_PENDING status and consolidate to QC_COMPLETED
-- This migration updates the workflow to eliminate the QC_PENDING status
-- by transitioning any documents in QC_PENDING state to QC_COMPLETED.

-- Update any inventory documents with QC_PENDING status to QC_COMPLETED
UPDATE inventory_documents
SET status = 'QC_COMPLETED'
WHERE status = 'QC_PENDING';

-- Verify migration was successful
-- SELECT COUNT(*) as qc_pending_count FROM inventory_documents WHERE status = 'QC_PENDING';
