package org.hvtra.iventoryservice.domain.entity.supplement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_supplement_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_supplement_requests_code",
                columnNames = "request_code"
        ),
        indexes = {
                @Index(name = "idx_supplement_requests_ref", columnList = "reference_type,reference_id"),
                @Index(name = "idx_supplement_requests_status", columnList = "status")
        }
)
@Getter
@Setter
public class InventorySupplementRequest extends BaseEntity {

    @Column(name = "request_code", nullable = false, length = 100)
    private String requestCode;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING_APPROVAL";

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventorySupplementRequestLine> lines = new ArrayList<>();

    public void addLine(InventorySupplementRequestLine line) {
        lines.add(line);
        line.setRequest(this);
    }
}
