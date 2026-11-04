package org.hvtra.iventoryservice.domain.entity.counting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.document.InventoryAdjustment;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryCountLockMode;
import org.hvtra.iventoryservice.domain.enums.InventoryCountType;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
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
                @Index(name = "idx_counts_status_started", columnList = "status,started_at")
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

    @Column(name = "supervisor_id", columnDefinition = "BINARY(16)")
    private UUID supervisorId;

    /** Thời điểm Start: khoá phạm vi và snapshot tồn. Movement sau mốc này được đối chiếu khi Finish. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** Thời điểm Finish (đã đếm hết, chốt chênh lệch). */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by", columnDefinition = "BINARY(16)")
    private UUID completedBy;

    /** Lý do của lần trả về đếm lại gần nhất. */
    @Column(name = "return_reason", length = 500)
    private String returnReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", columnDefinition = "BINARY(16)")
    private UUID cancelledBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjustment_document_id")
    private InventoryAdjustment adjustmentDocument;

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

    private static final EnumSet<InventoryCountStatus> OPEN_STATUSES = EnumSet.of(
            InventoryCountStatus.DRAFT,
            InventoryCountStatus.COUNTING,
            InventoryCountStatus.PENDING_APPROVAL);

    /**
     * DRAFT → COUNTING. Khoá phạm vi và đánh dấu mốc snapshot; service sinh dòng đếm ngay sau đó.
     */
    public void start() {
        if (this.status != InventoryCountStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể bắt đầu kiểm kê khi kỳ kiểm kê ở trạng thái DRAFT");
        }
        this.status = InventoryCountStatus.COUNTING;
        this.startedAt = Instant.now();
    }

    /**
     * COUNTING → COMPLETED (không chênh lệch) hoặc PENDING_APPROVAL (có chênh lệch).
     * Movement phải được đối chiếu trước khi gọi.
     */
    public void finish(UUID userId) {
        if (this.status != InventoryCountStatus.COUNTING) {
            throw new BadRequestException("Chỉ có thể kết thúc khi đang kiểm kê");
        }
        boolean allCounted = this.lines.stream().allMatch(InventoryCountLine::hasBeenCounted);
        if (!allCounted) {
            throw new BadRequestException("Tất cả dòng kiểm kê phải được đếm trước khi kết thúc");
        }
        this.lines.forEach(InventoryCountLine::finalizeVariance);
        this.submittedAt = Instant.now();
        if (getLinesWithVariance().isEmpty()) {
            markCompleted(userId);
        } else {
            this.status = InventoryCountStatus.PENDING_APPROVAL;
        }
    }

    /**
     * PENDING_APPROVAL → COMPLETED. Service tạo và ghi sổ phiếu điều chỉnh trong cùng transaction.
     */
    public void approve(UUID userId) {
        if (this.status != InventoryCountStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Kỳ kiểm kê không ở trạng thái chờ phê duyệt");
        }
        markCompleted(userId);
    }

    /**
     * PENDING_APPROVAL → COUNTING. Service reset các dòng cần đếm lại.
     */
    public void returnForRecount(String reason) {
        if (this.status != InventoryCountStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Chỉ có thể trả về đếm lại khi kỳ đang chờ phê duyệt");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Lý do đếm lại là bắt buộc");
        }
        this.status = InventoryCountStatus.COUNTING;
        this.returnReason = reason.trim();
    }

    /**
     * DRAFT / COUNTING / PENDING_APPROVAL → CANCELLED. Dùng cho cả huỷ và từ chối.
     */
    public void cancel(UUID userId, String reason) {
        if (!isOpen()) {
            throw new BadRequestException("Không thể hủy kỳ kiểm kê ở trạng thái hiện tại");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Lý do hủy kỳ kiểm kê là bắt buộc");
        }
        this.status = InventoryCountStatus.CANCELLED;
        this.cancelledBy = userId;
        this.cancelledAt = Instant.now();
        this.cancelReason = reason.trim();
    }

    private void markCompleted(UUID userId) {
        this.status = InventoryCountStatus.COMPLETED;
        this.completedBy = userId;
        this.completedAt = Instant.now();
    }

    // ====== Validation Guards ======

    public boolean isOpen() {
        return OPEN_STATUSES.contains(this.status);
    }

    /**
     * Kiểm tra có thể nhập số đếm không (status == COUNTING).
     */
    public void validateCanRecordCounts() {
        if (this.status != InventoryCountStatus.COUNTING) {
            throw new BadRequestException("Chỉ có thể nhập số đếm khi đang COUNTING");
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
    public void linkAdjustmentDocument(InventoryAdjustment document) {
        this.adjustmentDocument = document;
    }
}
