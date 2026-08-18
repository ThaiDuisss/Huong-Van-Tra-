package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_replenishment_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_replenishment_policy_item_loc",
                columnNames = {"inventory_item_id", "location_id"}
        ),
        indexes = @Index(name = "idx_replenishment_policy_enabled", columnList = "enabled")
)
@Getter
@Setter
public class InventoryReplenishmentPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @Column(name = "minimum_stock", nullable = false, precision = 18, scale = 2)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(name = "safety_stock", nullable = false, precision = 18, scale = 2)
    private BigDecimal safetyStock = BigDecimal.ZERO;

    @Column(name = "reorder_point", nullable = false, precision = 18, scale = 2)
    private BigDecimal reorderPoint = BigDecimal.ZERO;

    @Column(name = "standard_reorder_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal standardReorderQuantity = BigDecimal.ZERO;

    @Column(name = "supplier_lead_time_days", nullable = false)
    private Integer supplierLeadTimeDays = 0;

    @Column(name = "average_daily_consumption", nullable = false, precision = 18, scale = 2)
    private BigDecimal averageDailyConsumption = BigDecimal.ZERO;

    @Column(name = "maximum_stock", precision = 18, scale = 2)
    private BigDecimal maximumStock;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
}
