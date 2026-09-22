package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.math.BigDecimal;
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
}
