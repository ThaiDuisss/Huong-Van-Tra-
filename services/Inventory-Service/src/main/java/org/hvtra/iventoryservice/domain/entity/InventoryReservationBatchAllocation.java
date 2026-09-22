package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_reservation_batch_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_res_batch_alloc_res_batch",
                columnNames = {"reservation_id", "inventory_batch_id"}
        ),
        indexes = @Index(
                name = "idx_res_batch_alloc_batch_status",
                columnList = "inventory_batch_id,status"
        )
)
@Getter
@Setter
public class InventoryReservationBatchAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_batch_id", nullable = false)
    private InventoryBatch inventoryBatch;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ReservationStatus status = ReservationStatus.RESERVED;
}
