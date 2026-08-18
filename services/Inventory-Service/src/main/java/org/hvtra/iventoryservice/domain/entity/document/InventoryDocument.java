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

    @Column(name = "branch_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID branchId;

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

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryDocumentLine> lines = new ArrayList<>();

    public void addLine(InventoryDocumentLine line) {
        lines.add(line);
        line.setDocument(this);
    }
}
