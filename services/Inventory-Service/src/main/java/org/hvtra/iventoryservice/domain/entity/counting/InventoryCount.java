package org.hvtra.iventoryservice.domain.entity.counting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.document.InventoryDocument;
import org.hvtra.iventoryservice.domain.enums.InventoryCountScopeType;
import org.hvtra.iventoryservice.domain.enums.InventoryCountStatus;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 50)
    private InventoryCountScopeType scopeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InventoryCountStatus status = InventoryCountStatus.DRAFT;

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

    @OneToMany(mappedBy = "inventoryCount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryCountLine> lines = new ArrayList<>();

    public void addLine(InventoryCountLine line) {
        lines.add(line);
        line.setInventoryCount(this);
    }
}
