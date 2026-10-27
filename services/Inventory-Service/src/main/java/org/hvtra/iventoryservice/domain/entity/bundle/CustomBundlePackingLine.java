package org.hvtra.iventoryservice.domain.entity.bundle;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.iventoryservice.domain.entity.InventoryItem;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "custom_bundle_packing_lines")
@Getter
@Setter
public class CustomBundlePackingLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "packing_id", nullable = false)
    private CustomBundlePacking packing;

    /** Id nguyên liệu bên Catalog. */
    @Column(name = "ingredient_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID ingredientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "ingredient_name", length = 255)
    private String ingredientName;

    @Column(name = "required_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal requiredQuantity;

    /** Nhân viên kho đã lấy đủ nguyên liệu này khi đóng gói. */
    @Column(name = "picked", nullable = false)
    private boolean picked = false;
}
