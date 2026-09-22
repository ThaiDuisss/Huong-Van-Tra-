package org.hvtra.iventoryservice.domain.entity.counting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAssignmentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryCountLockMode;
import org.hvtra.iventoryservice.domain.enums.InventoryCountType;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_counts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_inventory_counts_code",
                columnNames = "count_code"
        ),
        indexes = {
                @Index(name = "idx_counts_branch_status", columnList = "branch_id,status"),
                @Index(name = "idx_counts_status_snapshot", columnList = "status,snapshot_at")
        }
)
@Getter
@Setter
public class InventoryCount extends BaseEntity {

    @Column(name = "count_code", nullable = false, length = 100)
    private String countCode;

    @Column(name = "branch_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(name = "warehouse_id", columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "count_type", nullable = false, length = 50)
    private InventoryCountType countType = InventoryCountType.FULL_COUNT;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 50)
    private InventoryCountScopeType scopeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InventoryCountStatus status = InventoryCountStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_mode", nullable = false, length = 30)
    private InventoryCountLockMode lockMode = InventoryCountLockMode.SOFT_LOCK;

    @Column(name = "blind_count", nullable = false)
    private Boolean blindCount = false;

    @Column(name = "planned_start_at")
    private Instant plannedStartAt;

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reconciling_at")
    private Instant reconcilingAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", columnDefinition = "BINARY(16)")
    private UUID closedBy;

    @Column(name = "supervisor_id", columnDefinition = "BINARY(16)")
    private UUID supervisorId;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "returned_by", columnDefinition = "BINARY(16)")
    private UUID returnedBy;

    @Column(name = "return_reason", length = 500)
    private String returnReason;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejected_by", columnDefinition = "BINARY(16)")
    private UUID rejectedBy;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "snapshot_at")
    private Instant snapshotAt;

    @Column(name = "counting_started_at")
    private Instant countingStartedAt;

    @Column(name = "counting_completed_at")
    private Instant countingCompletedAt;

    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_document_id")
    private InventoryDocument adjustmentDocument;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @ElementCollection
    @CollectionTable(
            name = "inventory_count_scope_locations",
            joinColumns = @JoinColumn(name = "inventory_count_id")
    )
    @Column(name = "location_id", nullable = false, columnDefinition = "BINARY(16)")
    private List<UUID> scopeLocationIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "inventory_count_scope_items",
            joinColumns = @JoinColumn(name = "inventory_count_id")
    )
    @Column(name = "inventory_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private List<UUID> scopeInventoryItemIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "inventory_count_scope_batches",
            joinColumns = @JoinColumn(name = "inventory_count_id")
    )
    @Column(name = "inventory_batch_id", nullable = false, columnDefinition = "BINARY(16)")
    private List<UUID> scopeBatchIds = new ArrayList<>();

    @OneToMany(mappedBy = "inventoryCount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryCountLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "inventoryCount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryCountAssignment> assignments = new ArrayList<>();

    public void addLine(InventoryCountLine line) {
        lines.add(line);
        line.setInventoryCount(this);
    }

    public void addAssignment(InventoryCountAssignment assignment) {
        assignments.add(assignment);
        assignment.setInventoryCount(this);
    }

    // ====== State Transition Methods ======

    public void freeze() {
        if (this.status != InventoryCountStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xác nhận phạm vi khi kỳ kiểm kê ở trạng thái DRAFT");
        }
        this.status = InventoryCountStatus.FROZEN;
        this.frozenAt = Instant.now();
    }

    /**
     * Chuyển DRAFT → COUNTING. Set snapshot time và counting start time.
     */
    public void start() {
        if (this.status != InventoryCountStatus.FROZEN) {
            throw new BadRequestException("Chỉ có thể bắt đầu kiểm kê sau khi phạm vi đã được xác nhận");
        }
        this.status = InventoryCountStatus.COUNTING;
        this.snapshotAt = Instant.now();
        this.countingStartedAt = Instant.now();
    }

    public void submitForReconciliation() {
        if (this.status != InventoryCountStatus.COUNTING) {
            throw new BadRequestException("Chỉ có thể gửi kết quả khi đang kiểm kê");
        }
        boolean allCounted = this.lines.stream().allMatch(InventoryCountLine::hasBeenCounted);
        if (!allCounted) {
            throw new BadRequestException("Tất cả dòng kiểm kê phải được đếm trước khi gửi đối soát");
        }
        this.lines.forEach(InventoryCountLine::finalizeVariance);
        this.status = InventoryCountStatus.RECONCILING;
        this.submittedAt = Instant.now();
        this.reconcilingAt = Instant.now();
    }

    public void submitForApproval(boolean requiresApproval, UUID decidedBy) {
        if (this.status != InventoryCountStatus.RECONCILING) {
            throw new BadRequestException("Chỉ có thể gửi phê duyệt khi đang đối soát");
        }
        this.status = requiresApproval
                ? InventoryCountStatus.PENDING_APPROVAL
                : InventoryCountStatus.APPROVED;
        if (!requiresApproval) {
            this.approvedBy = decidedBy;
            this.approvedAt = Instant.now();
        }
    }

    public void approve(UUID userId) {
        if (this.status != InventoryCountStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Kỳ kiểm kê không ở trạng thái chờ phê duyệt");
        }
        this.status = InventoryCountStatus.APPROVED;
        this.approvedBy = userId;
        this.approvedAt = Instant.now();
    }

    public void returnForRecount(UUID userId, String reason) {
        if (this.status != InventoryCountStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ có thể trả lại kỳ đang chờ phê duyệt");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Lý do trả lại là bắt buộc");
        }
        this.status = InventoryCountStatus.RETURNED;
        this.returnedBy = userId;
        this.returnedAt = Instant.now();
        this.returnReason = reason.trim();
    }

    public void reopenReturnedCount() {
        if (this.status != InventoryCountStatus.RETURNED) {
            throw new BadRequestException("Chỉ có thể kiểm kê lại kỳ đã được trả về");
        }
        this.status = InventoryCountStatus.COUNTING;
    }

    public void reopenForRecount() {
        if (this.status != InventoryCountStatus.RECONCILING) {
            throw new BadRequestException("Chỉ có thể yêu cầu kiểm kê lại khi đang đối soát");
        }
        this.status = InventoryCountStatus.COUNTING;
    }

    public void reject(UUID userId, String reason) {
        if (this.status != InventoryCountStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ có thể từ chối kỳ đang chờ phê duyệt");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Lý do từ chối là bắt buộc");
        }
        this.status = InventoryCountStatus.REJECTED;
        this.rejectedBy = userId;
        this.rejectedAt = Instant.now();
        this.rejectReason = reason.trim();
    }

    public void cancel(UUID userId, String reason) {
        if (this.status == InventoryCountStatus.APPROVED
                || this.status == InventoryCountStatus.POSTED
                || this.status == InventoryCountStatus.COMPLETED
                || this.status == InventoryCountStatus.CANCELLED
                || this.status == InventoryCountStatus.REJECTED) {
            throw new BadRequestException("Không thể hủy kỳ kiểm kê ở trạng thái hiện tại");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Lý do hủy kỳ kiểm kê là bắt buộc");
        }
        this.status = InventoryCountStatus.CANCELLED;
        this.closedBy = userId;
        this.closedAt = Instant.now();
        String cancellationNote = "Lý do hủy: " + reason.trim();
        this.note = this.note == null || this.note.isBlank()
                ? cancellationNote
                : this.note + "\n" + cancellationNote;
        this.assignments.forEach(assignment -> assignment.setStatus(InventoryCountAssignmentStatus.CANCELLED));
    }

    public void markPosted() {
        if (this.status != InventoryCountStatus.APPROVED) {
            throw new BadRequestException("Kỳ kiểm kê phải được duyệt trước khi hạch toán");
        }
        this.status = InventoryCountStatus.POSTED;
    }

    public void returnAfterAdjustmentRejected() {
        if (this.status != InventoryCountStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể trả lại kỳ đã duyệt nhưng chưa hạch toán");
        }
        this.adjustmentDocument = null;
        this.status = InventoryCountStatus.RECONCILING;
    }

    public void close(UUID userId) {
        boolean noAdjustmentRequired = this.status == InventoryCountStatus.APPROVED
                && getLinesWithVariance().isEmpty();
        if (this.status != InventoryCountStatus.POSTED && !noAdjustmentRequired) {
            throw new BadRequestException("Chỉ có thể đóng kỳ sau khi mọi chênh lệch đã được hạch toán");
        }
        this.status = InventoryCountStatus.COMPLETED;
        this.closedBy = userId;
        this.closedAt = Instant.now();
        this.countingCompletedAt = Instant.now();
    }

    /**
     * Chuyển COUNTING → COMPLETED. Validate tất cả lines đã đếm xong.
     */
    public void finish() {
        submitForReconciliation();
    }

    // ====== Validation Guards ======

    /**
     * Kiểm tra có thể nhập số đếm không (status == COUNTING).
     */
    public void validateCanRecordCounts() {
        if (this.status != InventoryCountStatus.COUNTING) {
            throw new BadRequestException("Chỉ có thể nhập số đếm khi đang COUNTING");
        }
    }

    /**
     * Kiểm tra có thể tạo phiếu điều chỉnh không (status == COMPLETED, chưa có adjustment doc).
     */
    public void validateCanCreateAdjustment() {
        if (this.status != InventoryCountStatus.APPROVED) {
            throw new BadRequestException("Phiên kiểm kê phải được phê duyệt trước khi tạo điều chỉnh");
        }
        if (this.adjustmentDocument != null) {
            throw new BadRequestException("Phiên kiểm kê đã có phiếu điều chỉnh");
        }
    }

    // ====== Query Methods ======

    /**
     * Lọc các lines có chênh lệch (variance != 0).
     */
    public List<InventoryCountLine> getLinesWithVariance() {
        return this.lines.stream()
                .filter(line -> line.getVarianceQuantity() != null
                        && line.getVarianceQuantity().compareTo(BigDecimal.ZERO) != 0)
                .toList();
    }

    /**
     * Gắn phiếu điều chỉnh vào phiên kiểm kê.
     */
    public void linkAdjustmentDocument(InventoryDocument document) {
        this.adjustmentDocument = document;
    }
}
