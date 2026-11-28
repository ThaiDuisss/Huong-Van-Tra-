package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.enums.InventoryLocationType;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_replenishment_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_replenishment_policy_item_type",
                columnNames = {"inventory_item_id", "location_type"}
        ),
        indexes = @Index(name = "idx_replenishment_policy_enabled", columnList = "enabled")
)
@Getter
@Setter
public class InventoryReplenishmentPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 50)
    private InventoryLocationType locationType;

    @Column(name = "minimum_stock", nullable = false, precision = 18, scale = 2)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(name = "maximum_stock", precision = 18, scale = 2)
    private BigDecimal maximumStock;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
