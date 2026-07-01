package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_payment_code", columnNames = "payment_code"),
                @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id")
        }
)
public class Payments extends BaseEntity {

    @Column(name = "payment_code", nullable = false, length = 50)
    private String paymentCode;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "saga_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID sagaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status;

    @Column(name = "amount_required", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountRequired;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "amount_refunded", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountRefunded = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency = "VND";

    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentTransaction> transactions = new ArrayList<>();
}