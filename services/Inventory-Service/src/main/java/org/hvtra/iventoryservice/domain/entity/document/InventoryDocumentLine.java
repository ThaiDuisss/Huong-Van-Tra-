package org.hvtra.iventoryservice.domain.entity.document;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_document_lines",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_document_lines_doc_line",
                columnNames = {"document_id", "line_no"}
        ),
        indexes = @Index(name = "idx_document_lines_item", columnList = "inventory_item_id")
)
@Getter
@Setter
public class InventoryDocumentLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private InventoryDocument document;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "unit_id", columnDefinition = "BINARY(16)")
    private UUID unitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_location_id")
    private InventoryLocation sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_location_id")
    private InventoryLocation destinationLocation;

    @Column(name = "ordered_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal orderedQuantity = BigDecimal.ZERO;

    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(name = "shortage_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal shortageQuantity = BigDecimal.ZERO;

    @Column(name = "excess_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal excessQuantity = BigDecimal.ZERO;

    @Column(name = "accepted_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal acceptedQuantity = BigDecimal.ZERO;

    @Column(name = "rejected_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal rejectedQuantity = BigDecimal.ZERO;

    @Column(name = "note", length = 500)
    private String note;

    @OneToMany(mappedBy = "documentLine", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<InventoryDocumentBatchAllocation> batchAllocations = new HashSet<>();

    public void addBatchAllocation(InventoryDocumentBatchAllocation allocation) {
        batchAllocations.add(allocation);
        allocation.setDocumentLine(this);
    }
}
