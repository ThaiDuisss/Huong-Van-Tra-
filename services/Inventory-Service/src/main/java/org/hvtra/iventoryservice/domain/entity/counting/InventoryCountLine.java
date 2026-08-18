package org.hvtra.iventoryservice.domain.entity.counting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.entity.InventoryLocation;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;

import java.math.BigDecimal;

@Entity
@Table(
        name = "inventory_count_lines",
        indexes = {
                @Index(name = "idx_count_lines_count_location", columnList = "inventory_count_id,location_id"),
                @Index(name = "idx_count_lines_item", columnList = "inventory_item_id"),
                @Index(name = "idx_count_lines_batch", columnList = "inventory_batch_id")
        }
)
@Getter
@Setter
public class InventoryCountLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_count_id", nullable = false)
    private InventoryCount inventoryCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private InventoryLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_batch_id")
    private InventoryBatch inventoryBatch;

    @Column(name = "snapshot_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal snapshotQuantity = BigDecimal.ZERO;

    @Column(name = "counted_quantity", precision = 18, scale = 2)
    private BigDecimal countedQuantity;

    @Column(name = "variance_quantity", precision = 18, scale = 2)
    private BigDecimal varianceQuantity;

    @Column(name = "reason_code", length = 100)
    private String reasonCode;

    @Column(name = "note", length = 500)
    private String note;
}
