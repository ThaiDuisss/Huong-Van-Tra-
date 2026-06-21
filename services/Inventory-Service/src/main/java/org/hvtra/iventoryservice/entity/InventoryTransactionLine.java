package org.hvtra.iventoryservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.math.BigDecimal;

@Entity
@Table(name = "inventory_transaction_lines")
@Getter
@Setter
public class InventoryTransactionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private InventoryTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    @Column(name = "before_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal beforeQuantity;

    @Column(name = "after_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal afterQuantity;
}
