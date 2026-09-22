package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.InventoryStatus;

import java.util.UUID;

@Entity
@Table(
        name = "inventory_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"reference_id", "item_type"})
)
@Getter
@Setter
public class InventoryItem extends BaseEntity {

    @Column(name = "reference_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID referenceId;

    @Column(name = "reference_code", length = 100)
    private String referenceCode;

    @Column(name = "name", length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 50)
    private InventoryItemType itemType;

    @Column(name = "unit_id", columnDefinition = "BINARY(16)")
    private UUID unitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InventoryStatus status = InventoryStatus.ACTIVE;

    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint;

    @Column(name = "batch_tracking_enabled", nullable = false)
    private Boolean batchTrackingEnabled = false;

    @Column(name = "expiry_tracking_enabled", nullable = false)
    private Boolean expiryTrackingEnabled = false;
}
