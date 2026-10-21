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
import java.time.LocalDate;

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

    /**
     * Single source of truth for sellable availability: onHand − reserved − blocked − expired.
     * A balance is tied to exactly one batch, so once that batch's expiry date has passed, its
     * entire onHand quantity is unsellable.
     */
    public BigDecimal getAvailableQuantity() {
        BigDecimal onHand = quantityOnHand != null ? quantityOnHand : BigDecimal.ZERO;
        BigDecimal reserved = quantityReserved != null ? quantityReserved : BigDecimal.ZERO;
        BigDecimal blocked = quantityBlocked != null ? quantityBlocked : BigDecimal.ZERO;
        BigDecimal expired = isExpired() ? onHand : BigDecimal.ZERO;
        BigDecimal available = onHand.subtract(reserved).subtract(blocked).subtract(expired);
        return available.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : available;
    }

    private boolean isExpired() {
        LocalDate expiryDate = inventoryBatch != null ? inventoryBatch.getExpiryDate() : null;
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }
}
