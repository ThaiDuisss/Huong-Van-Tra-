package org.hvtra.iventoryservice.domain.entity.production;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;

import java.math.BigDecimal;

@Entity
@Table(
        name = "production_order_material_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_prod_order_alloc_line_batch",
                columnNames = {"production_order_line_id", "inventory_batch_id"}
        )
)
@Getter
@Setter
public class ProductionOrderMaterialAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_line_id", nullable = false)
    private ProductionOrderLine productionOrderLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_batch_id", nullable = false)
    private InventoryBatch inventoryBatch;

    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "consumed_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReservationStatus status = ReservationStatus.RESERVED;
}
