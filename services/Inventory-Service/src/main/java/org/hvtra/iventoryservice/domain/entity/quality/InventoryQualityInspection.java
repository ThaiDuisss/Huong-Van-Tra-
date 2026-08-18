package org.hvtra.iventoryservice.domain.entity.quality;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.enums.InventoryQualityInspectionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_quality_inspections",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_quality_inspection_code",
                columnNames = "inspection_code"
        ),
        indexes = {
                @Index(name = "idx_quality_branch_status", columnList = "branch_id,status"),
                @Index(name = "idx_quality_source_document", columnList = "source_document_id")
        }
)
@Getter
@Setter
public class InventoryQualityInspection extends BaseEntity {

    @Column(name = "inspection_code", nullable = false, length = 100)
    private String inspectionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private InventoryDocument sourceDocument;

    @Column(name = "branch_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InventoryQualityInspectionStatus status = InventoryQualityInspectionStatus.DRAFT;

    @Column(name = "inspected_by", columnDefinition = "BINARY(16)")
    private UUID inspectedBy;

    @Column(name = "inspected_at")
    private Instant inspectedAt;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryQualityInspectionLine> lines = new ArrayList<>();

    public void addLine(InventoryQualityInspectionLine line) {
        lines.add(line);
        line.setInspection(this);
    }
}
