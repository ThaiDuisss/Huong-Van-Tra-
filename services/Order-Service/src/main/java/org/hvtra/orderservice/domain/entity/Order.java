package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.commonservice.domain.entity.BaseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tenant_id", columnDefinition = "CHAR(36)")
    private UUID tenantId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "branch_id", columnDefinition = "CHAR(36)")
    private UUID branchId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "terminal_id", columnDefinition = "CHAR(36)")
    private UUID terminalId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "shift_id", columnDefinition = "CHAR(36)")
    private UUID shiftId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "customer_id", columnDefinition = "CHAR(36)")
    private UUID customerId;

    @Column(name = "order_type", nullable = false, length = 50)
    private String orderType;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "payment_status", nullable = false, length = 50)
    private String paymentStatus;

    @Column(name = "fulfillment_status", length = 50)
    private String fulfillmentStatus;

    @Column(name = "original_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "shipping_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "final_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "debt_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal debtAmount;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(item);
        item.setOrder(this);
    }
}
