package org.hvtra.iventoryservice.domain.entity.movement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "transactionLine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryTransactionBatchAllocation> batchAllocations = new ArrayList<>();

    public void addBatchAllocation(InventoryTransactionBatchAllocation allocation) {
        batchAllocations.add(allocation);
        allocation.setTransactionLine(this);
    }
}
