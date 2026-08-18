package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_transaction_batch_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_trans_batch_alloc_line_batch",
                columnNames = {"transaction_line_id", "inventory_batch_id"}
        ),
        indexes = @Index(name = "idx_trans_batch_alloc_batch", columnList = "inventory_batch_id")
)
@Getter
@Setter
public class InventoryTransactionBatchAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_line_id", nullable = false)
    private InventoryTransactionLine transactionLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_batch_id", nullable = false)
    private InventoryBatch inventoryBatch;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "before_batch_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal beforeBatchQuantity = BigDecimal.ZERO;

    @Column(name = "after_batch_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal afterBatchQuantity = BigDecimal.ZERO;
}
