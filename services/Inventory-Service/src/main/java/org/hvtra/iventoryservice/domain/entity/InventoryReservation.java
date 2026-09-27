package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;
import org.hvtra.iventoryservice.domain.enums.ReservationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_inventory_reservation_scope",
                columnNames = {"order_id", "product_variant_id", "inventory_item_id", "location_id", "reservation_type", "processing_attempt"}
        )
)
@Getter
@Setter
public class InventoryReservation extends BaseEntity {

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(name = "order_item_id", columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    @Column(name = "ingredient_id", columnDefinition = "BINARY(16)")
    private UUID ingredientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_type", nullable = false, length = 50)
    private ReservationType reservationType = ReservationType.PRODUCT_VARIANT;

    @Column(name = "processing_attempt", nullable = false)
    private int processingAttempt = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ReservationStatus status = ReservationStatus.RESERVED;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryReservationBatchAllocation> batchAllocations = new ArrayList<>();

    public void addBatchAllocation(InventoryReservationBatchAllocation allocation) {
        batchAllocations.add(allocation);
        allocation.setReservation(this);
    }
}
