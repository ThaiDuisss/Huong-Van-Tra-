package org.hvtra.iventoryservice.domain.entity.production;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.batch.InventoryBatch;
import org.hvtra.iventoryservice.domain.enums.ReservationStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "production_order_material_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_prod_order_alloc_line_batch",
                columnNames = {"production_order_line_id", "inventory_batch_id"}
        )
)
@Getter
@Setter
public class ProductionOrderMaterialAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_line_id", nullable = false)
    private ProductionOrderLine productionOrderLine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_batch_id", nullable = false)
    private InventoryBatch inventoryBatch;

    /**
     * Kho thực tế đã xuất nguyên liệu này lúc duyệt (approve) — KHÔNG phải kho sản xuất/nhập thành phẩm
     * của {@code ProductionOrder.locationId}. Nguyên liệu không quản lý theo kho sản xuất, chỉ lấy theo
     * FEFO bất kể batch đang nằm ở kho nào, nên phải lưu lại đúng kho đã trừ tồn để release()/cancel()
     * hoàn tác/tiêu thụ đúng bản ghi tồn kho đó.
     */
    @Column(name = "location_id", columnDefinition = "BINARY(16)")
    private UUID locationId;

    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "consumed_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ReservationStatus status = ReservationStatus.RESERVED;
}
