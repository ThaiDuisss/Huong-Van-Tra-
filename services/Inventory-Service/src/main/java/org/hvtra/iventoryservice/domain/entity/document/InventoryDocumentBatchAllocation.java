package org.hvtra.iventoryservice.domain.entity.document;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_document_batch_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_doc_batch_alloc_line_batch",
                columnNames = {"document_line_id", "inventory_batch_id"}
        ),
        indexes = {
                @Index(name = "idx_doc_batch_alloc_line", columnList = "document_line_id"),
                @Index(name = "idx_doc_batch_alloc_batch", columnList = "inventory_batch_id")
        }
)
@Getter
@Setter
public class InventoryDocumentBatchAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_line_id", nullable = false)
    private InventoryDocumentLine documentLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_batch_id", nullable = false)
    private InventoryBatch inventoryBatch;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;
}
