ALTER TABLE inventory_counts
    ADD COLUMN warehouse_id BINARY(16) NULL AFTER branch_id,
    ADD COLUMN count_type VARCHAR(50) NOT NULL DEFAULT 'FULL_COUNT' AFTER warehouse_id,
    ADD COLUMN lock_mode VARCHAR(30) NOT NULL DEFAULT 'SOFT_LOCK' AFTER status,
    ADD COLUMN blind_count BOOLEAN NOT NULL DEFAULT FALSE AFTER lock_mode,
    ADD COLUMN planned_start_at DATETIME(6) NULL,
    ADD COLUMN planned_end_at DATETIME(6) NULL,
    ADD COLUMN frozen_at DATETIME(6) NULL,
    ADD COLUMN submitted_at DATETIME(6) NULL,
    ADD COLUMN reconciling_at DATETIME(6) NULL,
    ADD COLUMN closed_at DATETIME(6) NULL,
    ADD COLUMN closed_by BINARY(16) NULL,
    ADD COLUMN supervisor_id BINARY(16) NULL;

ALTER TABLE inventory_counts
    ADD COLUMN returned_at DATETIME(6) NULL,
    ADD COLUMN returned_by BINARY(16) NULL,
    ADD COLUMN return_reason VARCHAR(500) NULL,
    ADD COLUMN rejected_at DATETIME(6) NULL,
    ADD COLUMN rejected_by BINARY(16) NULL,
    ADD COLUMN reject_reason VARCHAR(500) NULL;

ALTER TABLE inventory_count_lines
    ADD COLUMN movement_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER snapshot_quantity,
    ADD COLUMN expected_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER movement_quantity,
    ADD COLUMN snapshot_unit_cost DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER expected_quantity,
    ADD COLUMN variance_value DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER variance_quantity,
    ADD COLUMN good_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER variance_quantity,
    ADD COLUMN damaged_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER good_quantity,
    ADD COLUMN expired_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER damaged_quantity,
    ADD COLUMN quarantined_quantity DECIMAL(18,2) NOT NULL DEFAULT 0.00 AFTER expired_quantity;

UPDATE inventory_count_lines
SET expected_quantity = snapshot_quantity
WHERE expected_quantity = 0.00;

CREATE TABLE inventory_count_scope_batches (
    inventory_count_id BINARY(16) NOT NULL,
    inventory_batch_id BINARY(16) NOT NULL,
    PRIMARY KEY (inventory_count_id, inventory_batch_id),
    CONSTRAINT fk_count_scope_batches_count
        FOREIGN KEY (inventory_count_id) REFERENCES inventory_counts(id),
    CONSTRAINT fk_count_scope_batches_batch
        FOREIGN KEY (inventory_batch_id) REFERENCES inventory_batches(id)
);

CREATE TABLE inventory_count_assignments (
    id BINARY(16) PRIMARY KEY,
    inventory_count_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    assignment_role VARCHAR(30) NOT NULL,
    location_id BINARY(16) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    accepted_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_by BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by BINARY(16) NULL,
    updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_count_assignments_count
        FOREIGN KEY (inventory_count_id) REFERENCES inventory_counts(id) ON DELETE CASCADE,
    CONSTRAINT fk_count_assignments_location
        FOREIGN KEY (location_id) REFERENCES inventory_locations(id),
    UNIQUE KEY uq_count_assignment (inventory_count_id, user_id, assignment_role, location_id)
);

CREATE TABLE inventory_count_attempts (
    id BINARY(16) PRIMARY KEY,
    count_line_id BINARY(16) NOT NULL,
    attempt_no INT NOT NULL,
    counter_id BINARY(16) NOT NULL,
    counted_quantity DECIMAL(18,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    note VARCHAR(500) NULL,
    submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by BINARY(16) NULL,
    updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_count_attempts_line
        FOREIGN KEY (count_line_id) REFERENCES inventory_count_lines(id) ON DELETE CASCADE,
    UNIQUE KEY uq_count_attempt (count_line_id, attempt_no)
);

CREATE INDEX idx_count_assignments_user_status
    ON inventory_count_assignments(user_id, status);

CREATE INDEX idx_count_attempts_line_status
    ON inventory_count_attempts(count_line_id, status);

CREATE TABLE inventory_count_approval_policies (
    id BINARY(16) PRIMARY KEY,
    branch_id BINARY(16) NULL,
    name VARCHAR(150) NOT NULL,
    auto_approve_quantity_limit DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    auto_approve_value_limit DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BINARY(16) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by BINARY(16) NULL,
    updated_at DATETIME(6) NULL ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_count_approval_policy_branch_active(branch_id, active)
);

INSERT INTO inventory_count_approval_policies (
    id, branch_id, name, auto_approve_quantity_limit,
    auto_approve_value_limit, active, version, is_deleted
) VALUES (
    UUID_TO_BIN('80000000-0000-0000-0000-000000000001'),
    NULL,
    'Chính sách phê duyệt kiểm kê mặc định',
    0.00,
    0.00,
    TRUE,
    0,
    FALSE
);
