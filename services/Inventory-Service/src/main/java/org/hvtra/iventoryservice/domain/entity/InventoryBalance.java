package org.hvtra.iventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_balances",
        uniqueConstraints = @UniqueConstraint(columnNames = {"inventory_item_id", "location_id"})
)
@Getter
@Setter
public class InventoryBalance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @Column(name = "quantity_on_hand", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(name = "quantity_reserved", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantityReserved = BigDecimal.ZERO;
}
