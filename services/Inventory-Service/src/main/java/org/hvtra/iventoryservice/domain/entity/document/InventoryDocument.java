package org.hvtra.iventoryservice.domain.entity.document;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryDocumentType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
                @Index(name = "idx_documents_warehouse", columnList = "warehouse_id"),
                @Index(name = "idx_documents_supplier", columnList = "supplier_id"),
                @Index(name = "idx_documents_reference", columnList = "reference_type,reference_id"),
                @Index(name = "idx_documents_posted_at", columnList = "posted_at")
        }
)
@Getter
@Setter
public class InventoryDocument extends BaseEntity {

    @Column(name = "document_code", nullable = false, length = 100)
    private String documentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private InventoryDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InventoryDocumentStatus status = InventoryDocumentStatus.DRAFT;

    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(name = "warehouse_id", columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", insertable = false, updatable = false)
    private InventoryLocation warehouse;

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

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryDocumentLine> lines = new ArrayList<>();

    public void addLine(InventoryDocumentLine line) {
        lines.add(line);
        line.setDocument(this);
    }
}
