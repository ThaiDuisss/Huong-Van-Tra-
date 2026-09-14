package org.hvtra.iventoryservice.domain.entity.production;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "production_order_lines")
@Getter
@Setter
public class ProductionOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_id", nullable = false)
    private ProductionOrder productionOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "ingredient_id", columnDefinition = "BINARY(16)")
    private UUID ingredientId;

    @Column(name = "ingredient_name", length = 255)
    private String ingredientName;

    @Column(name = "required_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal requiredQuantity;

    @Column(name = "unit_id", columnDefinition = "BINARY(16)")
    private UUID unitId;

    @Column(name = "waste_rate", precision = 9, scale = 4)
    private BigDecimal wasteRate;

    @Column(name = "reserved_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal reservedQuantity = BigDecimal.ZERO;

    @Column(name = "consumed_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal consumedQuantity = BigDecimal.ZERO;

    @OneToMany(mappedBy = "productionOrderLine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductionOrderMaterialAllocation> allocations = new ArrayList<>();

    public void addAllocation(ProductionOrderMaterialAllocation allocation) {
        allocations.add(allocation);
        allocation.setProductionOrderLine(this);
    }
}
