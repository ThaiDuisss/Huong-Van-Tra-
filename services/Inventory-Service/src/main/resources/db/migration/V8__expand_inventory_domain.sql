-- =====================================================================
-- V8__expand_inventory_domain.sql
-- Inventory-Service / Hương Vân Trà
-- Target: MySQL 8.x
--
-- Purpose
--   1) Add real batch/lot tracking + FEFO-ready balances.
--   2) Add inventory documents (PNK/PXK/adjustment/returns/production docs).
--   3) Add batch allocations for documents, transactions and reservations.
--   4) Add transfer, stock-count, quality and replenishment persistence.
--   5) Add batch genealogy for raw-material -> finished-product traceability.
--
-- IMPORTANT
--   - This migration is designed to run AFTER V7__add_inbox_events.sql.
--   - Existing UUID strategy is preserved: BINARY(16).
--   - Existing quantity scale is preserved: DECIMAL(18,2), so Hibernate
--     validation of current entities is not broken by a scale change.
--   - Existing inventory_items.reorder_point is kept for compatibility.
--     New per-location replenishment rules should use
--     inventory_replenishment_policies.
-- =====================================================================


-- =====================================================================
-- 1. EXTEND EXISTING CORE TABLES
-- =====================================================================

ALTER TABLE inventory_items
    ADD COLUMN batch_tracking_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER reorder_point,
    ADD COLUMN expiry_tracking_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER batch_tracking_enabled;

ALTER TABLE inventory_balances
    ADD COLUMN quantity_blocked DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER quantity_reserved;


-- =====================================================================
-- 2. BATCH / LOT
-- =====================================================================

CREATE TABLE inventory_batches (
                                   id BINARY(16) PRIMARY KEY,

                                   inventory_item_id BINARY(16) NOT NULL,

    -- Internal lot/batch code, e.g. LOT-TEA-20260818-001
                                   batch_code VARCHAR(100) NOT NULL,

    -- Optional supplier/manufacturer lot code printed on source package.
                                   supplier_batch_code VARCHAR(100) NULL,

                                   manufacture_date DATE NULL,
                                   expiry_date DATE NULL,

    -- AWAITING_QC / AVAILABLE / QUARANTINED / BLOCKED /
    -- DEPLETED / EXPIRED / RETURNED
                                   status VARCHAR(50) NOT NULL,

    -- Polymorphic origin reference:
    -- PURCHASE_ORDER / PRODUCTION_ORDER / CUSTOMER_RETURN / MANUAL ...
                                   source_type VARCHAR(50) NULL,
                                   source_reference_id VARCHAR(100) NULL,

                                   received_at DATETIME(6) NULL,

                                   created_by BINARY(16) NULL,
                                   created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                   updated_by BINARY(16) NULL,
                                   updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                   version BIGINT NULL,
                                   is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                   CONSTRAINT fk_batches_item
                                       FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),

                                   UNIQUE KEY uq_batches_item_code (inventory_item_id, batch_code),
                                   INDEX idx_batches_item_status (inventory_item_id, status),
                                   INDEX idx_batches_expiry (expiry_date),
                                   INDEX idx_batches_source (source_type, source_reference_id)
);


CREATE TABLE inventory_batch_balances (
                                          id BINARY(16) PRIMARY KEY,

                                          inventory_batch_id BINARY(16) NOT NULL,
                                          location_id BINARY(16) NOT NULL,

                                          quantity_on_hand DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                          quantity_reserved DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                          quantity_blocked DECIMAL(18,2) NOT NULL DEFAULT 0.00,

                                          created_by BINARY(16) NULL,
                                          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                          updated_by BINARY(16) NULL,
                                          updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                          version BIGINT NULL,
                                          is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                          CONSTRAINT fk_batch_balances_batch
                                              FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id),
                                          CONSTRAINT fk_batch_balances_location
                                              FOREIGN KEY (location_id) REFERENCES inventory_locations(id),

                                          UNIQUE KEY uq_batch_balances_batch_loc (inventory_batch_id, location_id),
                                          INDEX idx_batch_balances_location (location_id),
                                          INDEX idx_batch_balances_batch (inventory_batch_id)
);


-- =====================================================================
-- 3. INVENTORY DOCUMENTS
--
-- Document = business/approval record.
-- Transaction = immutable posted stock ledger.
--
-- Suggested document_type values:
-- PURCHASE_RECEIPT
-- PRODUCTION_RECEIPT
-- CUSTOMER_RETURN_RECEIPT
-- SALE_ISSUE
-- PRODUCTION_ISSUE
-- CUSTOM_BUNDLE_ISSUE
-- TRANSFER_ISSUE
-- TRANSFER_RECEIPT
-- SUPPLIER_RETURN
-- ADJUSTMENT_IN
-- ADJUSTMENT_OUT
-- WRITE_OFF
--
-- Suggested status values:
-- DRAFT / PENDING_APPROVAL / APPROVED / POSTED / CANCELLED
-- =====================================================================

CREATE TABLE inventory_documents (
                                     id BINARY(16) PRIMARY KEY,

                                     document_code VARCHAR(100) NOT NULL,
                                     document_type VARCHAR(50) NOT NULL,
                                     status VARCHAR(50) NOT NULL,

                                     branch_id BINARY(16) NOT NULL,

                                     source_location_id BINARY(16) NULL,
                                     destination_location_id BINARY(16) NULL,

    -- Reference to Order / PO / Production Order / Return / etc.
                                     reference_type VARCHAR(50) NULL,
                                     reference_id VARCHAR(100) NULL,

                                     reason_code VARCHAR(100) NULL,
                                     note TEXT NULL,

                                     approved_by BINARY(16) NULL,
                                     approved_at DATETIME(6) NULL,
                                     posted_at DATETIME(6) NULL,

                                     created_by BINARY(16) NULL,
                                     created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                     updated_by BINARY(16) NULL,
                                     updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                     version BIGINT NULL,
                                     is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                     CONSTRAINT fk_documents_source_location
                                         FOREIGN KEY (source_location_id) REFERENCES inventory_locations(id),
                                     CONSTRAINT fk_documents_destination_location
                                         FOREIGN KEY (destination_location_id) REFERENCES inventory_locations(id),

                                     UNIQUE KEY uq_inventory_documents_code (document_code),
                                     INDEX idx_documents_branch_status (branch_id, status),
                                     INDEX idx_documents_type_status (document_type, status),
                                     INDEX idx_documents_reference (reference_type, reference_id),
                                     INDEX idx_documents_posted_at (posted_at)
);


CREATE TABLE inventory_document_lines (
                                          id BINARY(16) PRIMARY KEY,

                                          document_id BINARY(16) NOT NULL,
                                          line_no INT NOT NULL,

                                          inventory_item_id BINARY(16) NOT NULL,

    -- Snapshot storage unit for the document line.
                                          unit_id BINARY(16) NOT NULL,

                                          source_location_id BINARY(16) NULL,
                                          destination_location_id BINARY(16) NULL,

    -- requested_quantity: expected/planned amount
    -- processed_quantity: actually accepted/issued/received amount
    -- rejected_quantity: damaged/rejected amount
                                          requested_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                          processed_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                          rejected_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,

                                          note VARCHAR(500) NULL,

                                          created_by BINARY(16) NULL,
                                          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                          updated_by BINARY(16) NULL,
                                          updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                          version BIGINT NULL,
                                          is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                          CONSTRAINT fk_document_lines_document
                                              FOREIGN KEY (document_id) REFERENCES inventory_documents(id)
                                                  ON DELETE CASCADE,
                                          CONSTRAINT fk_document_lines_item
                                              FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
                                          CONSTRAINT fk_document_lines_source_location
                                              FOREIGN KEY (source_location_id) REFERENCES inventory_locations(id),
                                          CONSTRAINT fk_document_lines_destination_location
                                              FOREIGN KEY (destination_location_id) REFERENCES inventory_locations(id),

                                          UNIQUE KEY uq_document_lines_doc_line (document_id, line_no),
                                          INDEX idx_document_lines_item (inventory_item_id)
);


CREATE TABLE inventory_document_batch_allocations (
                                                      id BINARY(16) PRIMARY KEY,

                                                      document_line_id BINARY(16) NOT NULL,
                                                      inventory_batch_id BINARY(16) NOT NULL,

                                                      quantity DECIMAL(18,2) NOT NULL,

                                                      created_by BINARY(16) NULL,
                                                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                                      updated_by BINARY(16) NULL,
                                                      updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                                      version BIGINT NULL,
                                                      is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                                      CONSTRAINT fk_doc_batch_alloc_line
                                                          FOREIGN KEY (document_line_id) REFERENCES inventory_document_lines(id)
                                                              ON DELETE CASCADE,
                                                      CONSTRAINT fk_doc_batch_alloc_batch
                                                          FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id),

                                                      UNIQUE KEY uq_doc_batch_alloc_line_batch (document_line_id, inventory_batch_id),
                                                      INDEX idx_doc_batch_alloc_batch (inventory_batch_id)
);


-- Link posted stock ledger to a business document.
ALTER TABLE inventory_transactions
    ADD COLUMN inventory_document_id BINARY(16) NULL AFTER branch_id,
    ADD CONSTRAINT fk_transactions_document
        FOREIGN KEY (inventory_document_id) REFERENCES inventory_documents(id),
    ADD INDEX idx_transactions_document (inventory_document_id);


-- =====================================================================
-- 4. BATCH TRACE ON IMMUTABLE STOCK MOVEMENTS
--
-- One transaction line may consume/receive multiple batches.
-- Do NOT put only one batch_id directly on inventory_transaction_lines.
-- =====================================================================

CREATE TABLE inventory_transaction_batch_allocations (
                                                         id BINARY(16) PRIMARY KEY,

                                                         transaction_line_id BINARY(16) NOT NULL,
                                                         inventory_batch_id BINARY(16) NOT NULL,

                                                         quantity DECIMAL(18,2) NOT NULL,

                                                         before_batch_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                         after_batch_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,

                                                         created_by BINARY(16) NULL,
                                                         created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                                         updated_by BINARY(16) NULL,
                                                         updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                                         version BIGINT NULL,
                                                         is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                                         CONSTRAINT fk_trans_batch_alloc_line
                                                             FOREIGN KEY (transaction_line_id) REFERENCES inventory_transaction_lines(id)
                                                                 ON DELETE CASCADE,
                                                         CONSTRAINT fk_trans_batch_alloc_batch
                                                             FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id),

                                                         UNIQUE KEY uq_trans_batch_alloc_line_batch (transaction_line_id, inventory_batch_id),
                                                         INDEX idx_trans_batch_alloc_batch (inventory_batch_id)
);


-- =====================================================================
-- 5. RESERVATION -> FEFO BATCH ALLOCATION
--
-- Existing inventory_reservations remains the reservation header/summary
-- for one order + variant + location.
-- This table stores how that reservation is split across lots.
-- =====================================================================

CREATE TABLE inventory_reservation_batch_allocations (
                                                         id BINARY(16) PRIMARY KEY,

                                                         reservation_id BINARY(16) NOT NULL,
                                                         inventory_batch_id BINARY(16) NOT NULL,

                                                         quantity DECIMAL(18,2) NOT NULL,

    -- RESERVED / CONFIRMED / RELEASED / EXPIRED
                                                         status VARCHAR(50) NOT NULL,

                                                         created_by BINARY(16) NULL,
                                                         created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                                         updated_by BINARY(16) NULL,
                                                         updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                                         version BIGINT NULL,
                                                         is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                                         CONSTRAINT fk_res_batch_alloc_reservation
                                                             FOREIGN KEY (reservation_id) REFERENCES inventory_reservations(id)
                                                                 ON DELETE CASCADE,
                                                         CONSTRAINT fk_res_batch_alloc_batch
                                                             FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id),

                                                         UNIQUE KEY uq_res_batch_alloc_res_batch (reservation_id, inventory_batch_id),
                                                         INDEX idx_res_batch_alloc_batch_status (inventory_batch_id, status)
);


-- =====================================================================
-- 6. TRANSFER WORKFLOW
--
-- Needed only because transfer has its own lifecycle:
-- REQUESTED -> APPROVED -> ISSUED -> IN_TRANSIT -> RECEIVED -> COMPLETED
-- =====================================================================

CREATE TABLE inventory_transfers (
                                     id BINARY(16) PRIMARY KEY,

                                     transfer_code VARCHAR(100) NOT NULL,

                                     source_location_id BINARY(16) NOT NULL,
                                     destination_location_id BINARY(16) NOT NULL,

                                     status VARCHAR(50) NOT NULL,

                                     reference_type VARCHAR(50) NULL,
                                     reference_id VARCHAR(100) NULL,

                                     requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                     approved_by BINARY(16) NULL,
                                     approved_at DATETIME(6) NULL,
                                     issued_at DATETIME(6) NULL,
                                     received_at DATETIME(6) NULL,
                                     completed_at DATETIME(6) NULL,

                                     issue_document_id BINARY(16) NULL,
                                     receipt_document_id BINARY(16) NULL,

                                     note TEXT NULL,

                                     created_by BINARY(16) NULL,
                                     created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                     updated_by BINARY(16) NULL,
                                     updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                     version BIGINT NULL,
                                     is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                     CONSTRAINT fk_transfers_source_location
                                         FOREIGN KEY (source_location_id) REFERENCES inventory_locations(id),
                                     CONSTRAINT fk_transfers_destination_location
                                         FOREIGN KEY (destination_location_id) REFERENCES inventory_locations(id),
                                     CONSTRAINT fk_transfers_issue_document
                                         FOREIGN KEY (issue_document_id) REFERENCES inventory_documents(id),
                                     CONSTRAINT fk_transfers_receipt_document
                                         FOREIGN KEY (receipt_document_id) REFERENCES inventory_documents(id),

                                     UNIQUE KEY uq_inventory_transfers_code (transfer_code),
                                     INDEX idx_transfers_status (status),
                                     INDEX idx_transfers_source_status (source_location_id, status),
                                     INDEX idx_transfers_destination_status (destination_location_id, status),
                                     INDEX idx_transfers_reference (reference_type, reference_id)
);


CREATE TABLE inventory_transfer_lines (
                                          id BINARY(16) PRIMARY KEY,

                                          transfer_id BINARY(16) NOT NULL,
                                          line_no INT NOT NULL,

                                          inventory_item_id BINARY(16) NOT NULL,
                                          unit_id BINARY(16) NOT NULL,

                                          requested_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                          issued_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                          received_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                          damaged_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,

                                          note VARCHAR(500) NULL,

                                          created_by BINARY(16) NULL,
                                          created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                          updated_by BINARY(16) NULL,
                                          updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                          version BIGINT NULL,
                                          is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                          CONSTRAINT fk_transfer_lines_transfer
                                              FOREIGN KEY (transfer_id) REFERENCES inventory_transfers(id)
                                                  ON DELETE CASCADE,
                                          CONSTRAINT fk_transfer_lines_item
                                              FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),

                                          UNIQUE KEY uq_transfer_lines_transfer_line (transfer_id, line_no),
                                          INDEX idx_transfer_lines_item (inventory_item_id)
);


CREATE TABLE inventory_transfer_batch_allocations (
                                                      id BINARY(16) PRIMARY KEY,

                                                      transfer_line_id BINARY(16) NOT NULL,
                                                      inventory_batch_id BINARY(16) NOT NULL,

                                                      issued_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                      received_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                      damaged_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,

                                                      created_by BINARY(16) NULL,
                                                      created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                                      updated_by BINARY(16) NULL,
                                                      updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                                      version BIGINT NULL,
                                                      is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                                      CONSTRAINT fk_transfer_batch_alloc_line
                                                          FOREIGN KEY (transfer_line_id) REFERENCES inventory_transfer_lines(id)
                                                              ON DELETE CASCADE,
                                                      CONSTRAINT fk_transfer_batch_alloc_batch
                                                          FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id),

                                                      UNIQUE KEY uq_transfer_batch_alloc_line_batch (transfer_line_id, inventory_batch_id),
                                                      INDEX idx_transfer_batch_alloc_batch (inventory_batch_id)
);


-- =====================================================================
-- 7. INVENTORY COUNT / STOCKTAKE
--
-- Flow:
-- DRAFT -> FROZEN -> COUNTING -> RECONCILING -> APPROVED -> POSTED
-- =====================================================================

CREATE TABLE inventory_counts (
                                  id BINARY(16) PRIMARY KEY,

                                  count_code VARCHAR(100) NOT NULL,
                                  branch_id BINARY(16) NOT NULL,

    -- FULL / LOCATION / PARTIAL
                                  scope_type VARCHAR(50) NOT NULL,

                                  status VARCHAR(50) NOT NULL,

                                  snapshot_at DATETIME(6) NULL,
                                  counting_started_at DATETIME(6) NULL,
                                  counting_completed_at DATETIME(6) NULL,

                                  approved_by BINARY(16) NULL,
                                  approved_at DATETIME(6) NULL,

                                  adjustment_document_id BINARY(16) NULL,

                                  note TEXT NULL,

                                  created_by BINARY(16) NULL,
                                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                  updated_by BINARY(16) NULL,
                                  updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                  version BIGINT NULL,
                                  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                  CONSTRAINT fk_counts_adjustment_document
                                      FOREIGN KEY (adjustment_document_id) REFERENCES inventory_documents(id),

                                  UNIQUE KEY uq_inventory_counts_code (count_code),
                                  INDEX idx_counts_branch_status (branch_id, status),
                                  INDEX idx_counts_status_snapshot (status, snapshot_at)
);


CREATE TABLE inventory_count_lines (
                                       id BINARY(16) PRIMARY KEY,

                                       inventory_count_id BINARY(16) NOT NULL,
                                       location_id BINARY(16) NOT NULL,
                                       inventory_item_id BINARY(16) NOT NULL,
                                       inventory_batch_id BINARY(16) NULL,

                                       snapshot_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                       counted_quantity DECIMAL(18,2) NULL,
                                       variance_quantity DECIMAL(18,2) NULL,

                                       reason_code VARCHAR(100) NULL,
                                       note VARCHAR(500) NULL,

                                       created_by BINARY(16) NULL,
                                       created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                       updated_by BINARY(16) NULL,
                                       updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                       version BIGINT NULL,
                                       is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                       CONSTRAINT fk_count_lines_count
                                           FOREIGN KEY (inventory_count_id) REFERENCES inventory_counts(id)
                                               ON DELETE CASCADE,
                                       CONSTRAINT fk_count_lines_location
                                           FOREIGN KEY (location_id) REFERENCES inventory_locations(id),
                                       CONSTRAINT fk_count_lines_item
                                           FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
                                       CONSTRAINT fk_count_lines_batch
                                           FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id),

                                       INDEX idx_count_lines_count_location (inventory_count_id, location_id),
                                       INDEX idx_count_lines_item (inventory_item_id),
                                       INDEX idx_count_lines_batch (inventory_batch_id)
);


-- =====================================================================
-- 8. QUALITY CONTROL
--
-- One inspection header can inspect multiple batches from one source document.
-- Suggested inspection status: DRAFT / IN_PROGRESS / COMPLETED / CANCELLED
-- Suggested line result: PASS / FAIL / PARTIAL
-- =====================================================================

CREATE TABLE inventory_quality_inspections (
                                               id BINARY(16) PRIMARY KEY,

                                               inspection_code VARCHAR(100) NOT NULL,

                                               source_document_id BINARY(16) NULL,
                                               branch_id BINARY(16) NOT NULL,

                                               status VARCHAR(50) NOT NULL,

                                               inspected_by BINARY(16) NULL,
                                               inspected_at DATETIME(6) NULL,

                                               note TEXT NULL,

                                               created_by BINARY(16) NULL,
                                               created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                               updated_by BINARY(16) NULL,
                                               updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                               version BIGINT NULL,
                                               is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                               CONSTRAINT fk_quality_source_document
                                                   FOREIGN KEY (source_document_id) REFERENCES inventory_documents(id),

                                               UNIQUE KEY uq_quality_inspection_code (inspection_code),
                                               INDEX idx_quality_branch_status (branch_id, status),
                                               INDEX idx_quality_source_document (source_document_id)
);


CREATE TABLE inventory_quality_inspection_lines (
                                                    id BINARY(16) PRIMARY KEY,

                                                    inspection_id BINARY(16) NOT NULL,
                                                    inventory_batch_id BINARY(16) NOT NULL,

                                                    inspected_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                    accepted_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                    rejected_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,

                                                    result VARCHAR(50) NOT NULL,
                                                    reason_code VARCHAR(100) NULL,
                                                    note VARCHAR(500) NULL,

                                                    created_by BINARY(16) NULL,
                                                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                                    updated_by BINARY(16) NULL,
                                                    updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                                    version BIGINT NULL,
                                                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                                    CONSTRAINT fk_quality_lines_inspection
                                                        FOREIGN KEY (inspection_id) REFERENCES inventory_quality_inspections(id)
                                                            ON DELETE CASCADE,
                                                    CONSTRAINT fk_quality_lines_batch
                                                        FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id),

                                                    UNIQUE KEY uq_quality_lines_inspection_batch (inspection_id, inventory_batch_id),
                                                    INDEX idx_quality_lines_batch (inventory_batch_id),
                                                    INDEX idx_quality_lines_result (result)
);


-- =====================================================================
-- 9. REPLENISHMENT
--
-- Reorder settings belong to ITEM + LOCATION, not globally to InventoryItem.
-- Existing inventory_items.reorder_point is intentionally kept as a legacy
-- default until application code is migrated.
-- =====================================================================

CREATE TABLE inventory_replenishment_policies (
                                                  id BINARY(16) PRIMARY KEY,

                                                  inventory_item_id BINARY(16) NOT NULL,
                                                  location_id BINARY(16) NOT NULL,

                                                  minimum_stock DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                  safety_stock DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                  reorder_point DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                  standard_reorder_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                  supplier_lead_time_days INT NOT NULL DEFAULT 0,
                                                  average_daily_consumption DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                  maximum_stock DECIMAL(18,2) NULL,

                                                  enabled BOOLEAN NOT NULL DEFAULT TRUE,

                                                  created_by BINARY(16) NULL,
                                                  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                                  updated_by BINARY(16) NULL,
                                                  updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                                  version BIGINT NULL,
                                                  is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                                  CONSTRAINT fk_replenishment_policy_item
                                                      FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
                                                  CONSTRAINT fk_replenishment_policy_location
                                                      FOREIGN KEY (location_id) REFERENCES inventory_locations(id),

                                                  UNIQUE KEY uq_replenishment_policy_item_loc (inventory_item_id, location_id),
                                                  INDEX idx_replenishment_policy_enabled (enabled)
);


CREATE TABLE inventory_replenishment_alerts (
                                                id BINARY(16) PRIMARY KEY,

                                                inventory_item_id BINARY(16) NOT NULL,
                                                location_id BINARY(16) NOT NULL,

    -- LOW_STOCK / OUT_OF_STOCK / REORDER_REQUIRED
                                                alert_type VARCHAR(50) NOT NULL,

    -- OPEN / ACKNOWLEDGED / ORDERED / RESOLVED
                                                status VARCHAR(50) NOT NULL,

                                                available_quantity_snapshot DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                reorder_point_snapshot DECIMAL(18,2) NOT NULL DEFAULT 0.00,
                                                suggested_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00,

                                                opened_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                                acknowledged_by BINARY(16) NULL,
                                                acknowledged_at DATETIME(6) NULL,
                                                ordered_at DATETIME(6) NULL,
                                                resolved_at DATETIME(6) NULL,

    -- Allows history while enforcing one currently-active alert
    -- for (item + location + alert_type).
                                                open_guard TINYINT
                                                    GENERATED ALWAYS AS (
                                                        CASE
                                                            WHEN status IN ('OPEN', 'ACKNOWLEDGED', 'ORDERED') THEN 1
                                                            ELSE NULL
                                                            END
                                                        ) STORED,

                                                created_by BINARY(16) NULL,
                                                created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                                updated_by BINARY(16) NULL,
                                                updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                                version BIGINT NULL,
                                                is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                                CONSTRAINT fk_replenishment_alert_item
                                                    FOREIGN KEY (inventory_item_id) REFERENCES inventory_items(id),
                                                CONSTRAINT fk_replenishment_alert_location
                                                    FOREIGN KEY (location_id) REFERENCES inventory_locations(id),

                                                UNIQUE KEY uq_replenishment_open_alert (
                                                    inventory_item_id,
                                                    location_id,
                                                    alert_type,
                                                    open_guard
                                                    ),
                                                INDEX idx_replenishment_alert_status (status),
                                                INDEX idx_replenishment_alert_opened (opened_at)
);


-- =====================================================================
-- 10. BATCH GENEALOGY / TRACEABILITY
--
-- Critical for:
-- raw material batch -> production -> finished product batch
--
-- Example:
-- parent_batch = raw tea LOT-RAW-001
-- child_batch  = tea-box LOT-FG-001
-- relation_type = PRODUCTION
-- reference_id = production order id
-- =====================================================================

CREATE TABLE inventory_batch_relations (
                                           id BINARY(16) PRIMARY KEY,

                                           parent_batch_id BINARY(16) NOT NULL,
                                           child_batch_id BINARY(16) NOT NULL,

                                           relation_type VARCHAR(50) NOT NULL,

                                           reference_type VARCHAR(50) NULL,
                                           reference_id VARCHAR(100) NULL,

                                           quantity_used DECIMAL(18,2) NOT NULL DEFAULT 0.00,

                                           created_by BINARY(16) NULL,
                                           created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                           updated_by BINARY(16) NULL,
                                           updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
                                           version BIGINT NULL,
                                           is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

                                           CONSTRAINT fk_batch_rel_parent
                                               FOREIGN KEY (parent_batch_id) REFERENCES inventory_batches(id),
                                           CONSTRAINT fk_batch_rel_child
                                               FOREIGN KEY (child_batch_id) REFERENCES inventory_batches(id),

                                           UNIQUE KEY uq_batch_relation (
                                               parent_batch_id,
                                               child_batch_id,
                                               relation_type,
                                               reference_id
                                               ),
                                           INDEX idx_batch_rel_parent (parent_batch_id),
                                           INDEX idx_batch_rel_child (child_batch_id),
                                           INDEX idx_batch_rel_reference (reference_type, reference_id)
);

-- =====================================================================
-- END V8
-- =====================================================================
