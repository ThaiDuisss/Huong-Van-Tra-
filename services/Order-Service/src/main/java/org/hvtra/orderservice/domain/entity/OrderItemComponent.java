package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;

import java.math.BigDecimal;
import java.util.UUID;

/** Nguyên liệu khách chọn trong một dòng bundle; giá chốt tại lúc thêm vào đơn. */
@Entity
@Table(name = "order_item_components")
@Getter
@Setter
public class OrderItemComponent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private OrderItem orderItem;

    @Column(name = "ingredient_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID ingredientId;

    @Column(name = "ingredient_code_snapshot", length = 100)
    private String ingredientCodeSnapshot;

    @Column(name = "ingredient_name_snapshot", nullable = false)
    private String ingredientNameSnapshot;

    @Column(name = "unit_snapshot", length = 50)
    private String unitSnapshot;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;
}
