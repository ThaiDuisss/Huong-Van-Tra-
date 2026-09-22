package org.hvtra.iventoryservice.domain.entity.supplement;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_supplement_request_lines",
        indexes = {
                @Index(name = "idx_supplement_lines_request", columnList = "request_id"),
                @Index(name = "idx_supplement_lines_item", columnList = "inventory_item_id")
        }
)
@Getter
@Setter
public class InventorySupplementRequestLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private InventorySupplementRequest request;

    @Column(name = "receipt_line_id", columnDefinition = "BINARY(16)")
    private UUID receiptLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "reason", length = 255)
    private String reason;
}
