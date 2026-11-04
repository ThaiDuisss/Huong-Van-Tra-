package org.hvtra.iventoryservice.domain.entity.counting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAssignmentRole;
import org.hvtra.iventoryservice.domain.enums.InventoryCountAssignmentStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_count_assignments")
@Getter
@Setter
public class InventoryCountAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_count_id", nullable = false)
    private InventoryCount inventoryCount;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 30)
    private InventoryCountAssignmentRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private InventoryLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InventoryCountAssignmentStatus status = InventoryCountAssignmentStatus.ASSIGNED;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public void markDone() {
        status = InventoryCountAssignmentStatus.DONE;
        completedAt = Instant.now();
    }

    public void reopen() {
        status = InventoryCountAssignmentStatus.ASSIGNED;
        completedAt = null;
    }
}
