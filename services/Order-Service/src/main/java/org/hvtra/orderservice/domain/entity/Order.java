package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
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

    @Column(name = "tenant_id", columnDefinition = "BINARY(16)")
    private UUID tenantId;

    @Column(name = "branch_id", columnDefinition = "BINARY(16)")
    private UUID branchId;

    @Column(name = "terminal_id", columnDefinition = "BINARY(16)")
    private UUID terminalId;

    @Column(name = "shift_id", columnDefinition = "BINARY(16)")
    private UUID shiftId;

    @Column(name = "customer_id", columnDefinition = "BINARY(16)")
    private UUID customerId;

    @Column(name = "order_type", nullable = false, length = 50)
    private String orderType;

    @Column(name = "customer_name_snapshot")
    private String customerNameSnapshot;

    @Column(name = "customer_phone_snapshot", length = 30)
    private String customerPhoneSnapshot;

    @Column(name = "customer_email_snapshot")
    private String customerEmailSnapshot;

    @Column(name = "branch_name_snapshot")
    private String branchNameSnapshot;

    @Column(name = "terminal_name_snapshot")
    private String terminalNameSnapshot;

    @Column(name = "shift_code_snapshot", length = 100)
    private String shiftCodeSnapshot;

    @Column(name = "cashier_id", columnDefinition = "BINARY(16)")
    private UUID cashierId;

    @Column(name = "cashier_name_snapshot")
    private String cashierNameSnapshot;

    @Column(name = "recipient_name_snapshot")
    private String recipientNameSnapshot;

    @Column(name = "recipient_phone_snapshot", length = 30)
    private String recipientPhoneSnapshot;

    @Column(name = "shipping_address_snapshot", length = 500)
    private String shippingAddressSnapshot;

    @Column(name = "shipping_provider_snapshot")
    private String shippingProviderSnapshot;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "payment_status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_code", length = 100)
    private String paymentCode;

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

    @Column(name = "expires_at")
    private Instant expiresAt;

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
