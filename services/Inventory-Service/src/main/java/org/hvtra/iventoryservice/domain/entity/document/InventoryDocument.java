package org.hvtra.iventoryservice.domain.entity.document;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DiscriminatorFormula;
import org.hibernate.annotations.DiscriminatorOptions;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chứng từ kho dùng chung một bảng; mỗi loại là một lớp con (single-table inheritance) phân biệt theo
 * {@code document_type}: {@link GoodsReceipt}, {@link ShelfReplenishment}, {@link InventoryAdjustment}.
 * Phiếu điều chỉnh lưu 2 giá trị ADJUSTMENT_IN / ADJUSTMENT_OUT nhưng chung một lớp, nên phân biệt bằng
 * công thức thay vì cột thô. Loại phiếu được chốt khi khởi tạo, không đổi sau đó.
 */
@Entity
@Table(
        name = "inventory_documents",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_inventory_documents_code",
                columnNames = "document_code"
        ),
        indexes = {
                @Index(name = "idx_documents_branch_status", columnList = "branch_id,status"),
                @Index(name = "idx_documents_type_status", columnList = "document_type,status"),
                @Index(name = "idx_documents_location", columnList = "location_id"),
                @Index(name = "idx_documents_supplier", columnList = "supplier_id"),
                @Index(name = "idx_documents_reference", columnList = "reference_type,reference_id"),
                @Index(name = "idx_documents_posted_at", columnList = "posted_at")
        }
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula(
        value = "CASE WHEN document_type IN ('ADJUSTMENT_IN', 'ADJUSTMENT_OUT') THEN 'ADJUSTMENT' ELSE document_type END",
        discriminatorType = DiscriminatorType.STRING)
// Giá trị phân biệt chỉ dùng khi đọc; cột document_type được ghi từ trường documentType
@DiscriminatorOptions(insert = false)
@Getter
@Setter
public abstract class InventoryDocument extends BaseEntity {

    @Column(name = "document_code", nullable = false, length = 100)
    private String documentCode;

    /**
     * Gán qua constructor của lớp con; không có setter để loại phiếu không bị đổi sau khi tạo.
     * Khai báo VARCHAR (khớp cột thật) để công thức phân biệt so sánh chuỗi được trên mọi DB.
     */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "document_type", nullable = false, length = 50, updatable = false)
    private InventoryDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InventoryDocumentStatus status = InventoryDocumentStatus.DRAFT;

    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(name = "location_id", columnDefinition = "BINARY(16)")
    private UUID locationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", insertable = false, updatable = false)
    private InventoryLocation location;

    @Column(name = "supplier_id", columnDefinition = "BINARY(16)")
    private UUID supplierId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id")
    private InventoryLocation sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private InventoryLocation destinationLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 50)
    private InventoryReferenceType referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "expected_date")
    private Instant expectedDate;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_note", columnDefinition = "TEXT")
    private String approvalNote;

    @Column(name = "rejected_by", columnDefinition = "BINARY(16)")
    private UUID rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "received_by", columnDefinition = "BINARY(16)")
    private UUID receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "qc_by", columnDefinition = "BINARY(16)")
    private UUID qcBy;

    @Column(name = "qc_at")
    private Instant qcAt;

    @Column(name = "posted_by", columnDefinition = "BINARY(16)")
    private UUID postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    /** Người duyệt ngoại lệ (thừa / thiếu / hỏng) trước khi ghi sổ. */
    @Column(name = "exception_approved_by", columnDefinition = "BINARY(16)")
    private UUID exceptionApprovedBy;

    @Column(name = "exception_approved_at")
    private Instant exceptionApprovedAt;

    @Column(name = "exception_approval_note", columnDefinition = "TEXT")
    private String exceptionApprovalNote;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryDocumentLine> lines = new ArrayList<>();

    /** Cho Hibernate khi nạp từ DB; documentType được đọc từ cột. */
    protected InventoryDocument() {
    }

    /** Lớp con truyền đúng loại của mình; giá trị này được ghi xuống cột document_type khi lưu. */
    protected InventoryDocument(InventoryDocumentType documentType) {
        this.documentType = documentType;
    }

    public void addLine(InventoryDocumentLine line) {
        lines.add(line);
        line.setDocument(this);
    }

//     Phê duyệt phiếu: DRAFT → APPROVED.
    public void approve(UUID approvedByUser) {
        if (this.status != InventoryDocumentStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể phê duyệt phiếu ở trạng thái DRAFT");
        }
        this.status = InventoryDocumentStatus.APPROVED;
        this.approvedBy = approvedByUser;
        this.approvedAt = Instant.now();
    }

//     Hạch toán phiếu: APPROVED → POSTED
    public void post() {
        if (this.status != InventoryDocumentStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể hạch toán phiếu đã được phê duyệt");
        }
        this.status = InventoryDocumentStatus.POSTED;
        this.postedAt = Instant.now();
    }

//     Từ chối phiếu: DRAFT/APPROVED → REJECTED.
    public void reject() {
        if (this.status == InventoryDocumentStatus.POSTED) {
            throw new BadRequestException("Không thể từ chối phiếu đã hạch toán");
        }
        if (this.status == InventoryDocumentStatus.REJECTED) {
            throw new BadRequestException("Phiếu đã bị từ chối");
        }
        this.status = InventoryDocumentStatus.REJECTED;
    }

    // ====== Validation Guards ======

    public void validateCanPost() {
        if (this.status != InventoryDocumentStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể hạch toán phiếu đã được phê duyệt");
        }
    }
}
