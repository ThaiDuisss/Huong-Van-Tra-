package org.hvtra.iventoryservice.domain.entity.batch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchRelationType;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_batch_relations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batch_relation",
                columnNames = {
                        "parent_batch_id",
                        "child_batch_id",
                        "relation_type",
                        "reference_id"
                }
        ),
        indexes = {
                @Index(name = "idx_batch_rel_parent", columnList = "parent_batch_id"),
                @Index(name = "idx_batch_rel_child", columnList = "child_batch_id"),
                @Index(name = "idx_batch_rel_reference", columnList = "reference_type,reference_id")
        }
)
@Getter
@Setter
public class InventoryBatchRelation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_batch_id", nullable = false)
    private InventoryBatch parentBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_batch_id", nullable = false)
    private InventoryBatch childBatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 50)
    private InventoryBatchRelationType relationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 50)
    private InventoryReferenceType referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "quantity_used", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantityUsed = BigDecimal.ZERO;
}
