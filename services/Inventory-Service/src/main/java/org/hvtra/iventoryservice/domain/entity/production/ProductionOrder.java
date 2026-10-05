package org.hvtra.iventoryservice.domain.entity.production;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;
import org.hvtra.iventoryservice.domain.enums.ProductionOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "production_orders",
        uniqueConstraints = @UniqueConstraint(name = "uq_production_orders_code", columnNames = "order_code"),
        indexes = {
                @Index(name = "idx_production_orders_status", columnList = "status"),
                @Index(name = "idx_production_orders_variant", columnList = "product_variant_id")
        }
)
@Getter
@Setter
public class ProductionOrder extends BaseEntity {

    @Column(name = "order_code", nullable = false, length = 100)
    private String orderCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id", nullable = false)
    private InventoryItem productVariant;

    @Column(name = "planned_quantity", nullable = false, precision = 18, scale = 2)
    private BigDecimal plannedQuantity;

    @Column(name = "unit_id", columnDefinition = "BINARY(16)")
    private UUID unitId;

    @Column(name = "location_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProductionOrderStatus status = ProductionOrderStatus.DRAFT;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "submitted_by", columnDefinition = "BINARY(16)")
    private UUID submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_by", columnDefinition = "BINARY(16)")
    private UUID rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "cancelled_by", columnDefinition = "BINARY(16)")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "expected_completion_date")
    private Instant expectedCompletionDate;

    @OneToMany(mappedBy = "productionOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductionOrderLine> lines = new ArrayList<>();

    public void addLine(ProductionOrderLine line) {
        lines.add(line);
        line.setProductionOrder(this);
    }
}
