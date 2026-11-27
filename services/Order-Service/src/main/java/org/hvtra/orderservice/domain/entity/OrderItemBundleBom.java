package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Công thức (BOM) được chọn trong một dòng trà đóng theo yêu cầu kèm ghi chú riêng của khách. */
@Entity
@Table(name = "order_item_bundle_boms")
@Getter
@Setter
public class OrderItemBundleBom extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private OrderItem orderItem;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "source_variant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID sourceVariantId;

    @Column(name = "bom_sku_snapshot", length = 100)
    private String bomSkuSnapshot;

    @Column(name = "bom_name_snapshot", nullable = false)
    private String bomNameSnapshot;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "estimated_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal estimatedPrice = BigDecimal.ZERO;

    @BatchSize(size = 50)
    @OneToMany(mappedBy = "bundleBom", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<OrderItemBundleBomLine> lines = new ArrayList<>();

    public void replaceLines(List<OrderItemBundleBomLine> newLines) {
        if (lines == null) {
            lines = new ArrayList<>();
        }
        lines.clear();
        newLines.forEach(line -> {
            line.setBundleBom(this);
            lines.add(line);
        });
    }
}
