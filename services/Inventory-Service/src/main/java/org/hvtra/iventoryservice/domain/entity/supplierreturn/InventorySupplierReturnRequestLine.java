package org.hvtra.iventoryservice.domain.entity.supplierreturn;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_supplier_return_request_lines",
        indexes = {
                @Index(name = "idx_supplier_return_lines_request", columnList = "request_id"),
                @Index(name = "idx_supplier_return_lines_item", columnList = "inventory_item_id")
        }
)
@Getter
@Setter
public class InventorySupplierReturnRequestLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private InventorySupplierReturnRequest request;

    @Column(name = "receipt_line_id", columnDefinition = "BINARY(16)")
    private UUID receiptLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_batch_id")
    private InventoryBatch inventoryBatch;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reason", length = 255)
    private String reason;
}
