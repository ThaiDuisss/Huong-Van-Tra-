package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hvtra.commonservice.common_core.domain.entity.BaseEntity;
import org.hvtra.orderservice.domain.enums.DiscountScope;
import org.hvtra.orderservice.domain.enums.DiscountSource;
import org.hvtra.orderservice.domain.enums.DiscountStatus;
import org.hvtra.orderservice.domain.enums.DiscountType;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_discounts")
@Getter
@Setter
@NoArgsConstructor
public class OrderDiscount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private DiscountScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private DiscountSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "usage_id", columnDefinition = "BINARY(16)")
    private UUID usageId;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal discountValue;

    @Column(name = "base_amount", precision = 14, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DiscountStatus status;

    @Column(name = "applied_by", columnDefinition = "BINARY(16)")
    private UUID appliedBy;

    @Column(name = "applied_by_name_snapshot")
    private String appliedByNameSnapshot;
}
