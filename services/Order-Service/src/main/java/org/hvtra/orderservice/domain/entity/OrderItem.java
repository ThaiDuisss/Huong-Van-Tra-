package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.orderservice.domain.enums.BundlePackingStatus;
import org.hvtra.orderservice.domain.enums.OrderItemType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private Order order;

    @Column(name = "product_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productVariantId;

    @Column(name = "sku_snapshot", length = 100)
    private String skuSnapshot;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "variant_name_snapshot")
    private String variantNameSnapshot;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "original_unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalUnitPrice;

    @Column(name = "actual_unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal actualUnitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "bom_version_id", columnDefinition = "BINARY(16)")
    private UUID bomVersionId;

    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    @Builder.Default
    private OrderItemType itemType = OrderItemType.PRODUCT;

    /** Chỉ dùng cho dòng CUSTOM_BUNDLE: trạng thái yêu cầu đóng gói bên Inventory. */
    @Enumerated(EnumType.STRING)
    @Column(name = "packing_status", length = 30)
    private BundlePackingStatus packingStatus;

    @Column(name = "packing_reference_id", columnDefinition = "BINARY(16)")
    private UUID packingReferenceId;

    @Column(name = "packing_code", length = 100)
    private String packingCode;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    @Builder.Default
    private List<OrderItemComponent> components = new ArrayList<>();

    public boolean isCustomBundle() {
        return itemType == OrderItemType.CUSTOM_BUNDLE;
    }

    @PrePersist
    void defaultItemType() {
        if (itemType == null) {
            itemType = OrderItemType.PRODUCT;
        }
    }

    public void replaceComponents(List<OrderItemComponent> newComponents) {
        if (components == null) {
            components = new ArrayList<>();
        }
        components.clear();
        newComponents.forEach(component -> {
            component.setOrderItem(this);
            components.add(component);
        });
    }
}
