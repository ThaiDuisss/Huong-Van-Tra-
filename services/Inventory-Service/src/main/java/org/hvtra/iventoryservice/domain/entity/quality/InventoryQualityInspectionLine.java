package org.hvtra.iventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.enums.InventoryQualityResult;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_quality_inspection_lines",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_quality_lines_inspection_batch",
                columnNames = {"inspection_id", "inventory_batch_id"}
        ),
        indexes = {
                @Index(name = "idx_quality_lines_batch", columnList = "inventory_batch_id"),
                @Index(name = "idx_quality_lines_result", columnList = "result")
        }
)
@Getter
@Setter
public class InventoryQualityInspectionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private InventoryQualityInspection inspection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_batch_id", nullable = false)
    private InventoryBatch inventoryBatch;

    @Column(name = "inspected_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal inspectedQuantity = BigDecimal.ZERO;

    @Column(name = "accepted_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal acceptedQuantity = BigDecimal.ZERO;

    @Column(name = "rejected_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal rejectedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 50)
    private InventoryQualityResult result;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "note", length = 500)
    private String note;
}
