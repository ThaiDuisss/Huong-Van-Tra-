package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryReplenishmentAlertType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_replenishment_alerts",
        indexes = {
                @Index(name = "idx_replenishment_alert_status", columnList = "status"),
                @Index(name = "idx_replenishment_alert_opened", columnList = "opened_at")
        }
)
@Getter
@Setter
public class InventoryReplenishmentAlert extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private InventoryReplenishmentAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InventoryReplenishmentAlertStatus status = InventoryReplenishmentAlertStatus.OPEN;

    @Column(name = "available_quantity_snapshot", nullable = false, precision = 18, scale = 2)
    private BigDecimal availableQuantitySnapshot = BigDecimal.ZERO;

    @Column(name = "reorder_point_snapshot", nullable = false, precision = 18, scale = 2)
    private BigDecimal reorderPointSnapshot = BigDecimal.ZERO;

    @Column(name = "suggested_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal suggestedQuantity = BigDecimal.ZERO;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "acknowledged_by", columnDefinition = "BINARY(16)")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /*
     * open_guard is a generated column in MySQL and is intentionally
     * read-only from JPA. It enforces one active alert per
     * (item, location, alertType).
     */
    @Column(name = "open_guard", insertable = false, updatable = false)
    private Byte openGuard;
}
