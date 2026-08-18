package org.hvtra.iventoryservice.domain.entity.batch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_batch_balances",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batch_balances_batch_loc",
                columnNames = {"inventory_batch_id", "location_id"}
        ),
        indexes = {
                @Index(name = "idx_batch_balances_location", columnList = "location_id"),
                @Index(name = "idx_batch_balances_batch", columnList = "inventory_batch_id")
        }
)
@Getter
@Setter
public class InventoryBatchBalance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_batch_id", nullable = false)
    private InventoryBatch inventoryBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @Column(name = "quantity_on_hand", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(name = "quantity_reserved", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantityReserved = BigDecimal.ZERO;

    @Column(name = "quantity_blocked", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantityBlocked = BigDecimal.ZERO;

    public BigDecimal getAvailableQuantity() {
        return quantityOnHand
                .subtract(quantityReserved)
                .subtract(quantityBlocked);
    }
}
