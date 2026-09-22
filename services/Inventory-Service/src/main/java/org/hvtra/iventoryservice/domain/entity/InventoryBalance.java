package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_balances",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_inventory_balances_batch_loc",
                columnNames = {"inventory_batch_id", "location_id"}
        ),
        indexes = {
                @Index(name = "idx_inventory_balances_item", columnList = "inventory_item_id"),
                @Index(name = "idx_inventory_balances_batch", columnList = "inventory_batch_id"),
                @Index(name = "idx_inventory_balances_location", columnList = "location_id")
        }
)
@Getter
@Setter
public class InventoryBalance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

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
        BigDecimal onHand = quantityOnHand != null ? quantityOnHand : BigDecimal.ZERO;
        BigDecimal reserved = quantityReserved != null ? quantityReserved : BigDecimal.ZERO;
        BigDecimal blocked = quantityBlocked != null ? quantityBlocked : BigDecimal.ZERO;
        BigDecimal available = onHand.subtract(reserved).subtract(blocked);
        return available.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : available;
    }
}
