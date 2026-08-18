package org.hvtra.iventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.shared.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "product_variant_id", "location_id"})
)
@Getter
@Setter
public class InventoryReservation extends BaseEntity {

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

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
}
