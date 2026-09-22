package org.hvtra.iventoryservice.domain.entity.batch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.enums.InventoryBatchStatus;
import org.hvtra.iventoryservice.domain.enums.InventoryReferenceType;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "inventory_batches",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_batches_item_code",
                columnNames = {"inventory_item_id", "batch_code"}
        ),
        indexes = {
                @Index(name = "idx_batches_item_status", columnList = "inventory_item_id,status"),
                @Index(name = "idx_batches_expiry", columnList = "expiry_date"),
                @Index(name = "idx_batches_source", columnList = "source_type,source_reference_id")
        }
)
@Getter
@Setter
public class InventoryBatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "batch_code", nullable = false, length = 100)
    private String batchCode;

    @Column(name = "supplier_batch_code", length = 100)
    private String supplierBatchCode;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private InventoryBatchStatus status = InventoryBatchStatus.AWAITING_QC;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 50)
    private InventoryReferenceType sourceType;

    @Column(name = "source_reference_id", length = 100)
    private String sourceReferenceId;

    @Column(name = "received_at")
    private Instant receivedAt;
}
