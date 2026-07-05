package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
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
        },
        indexes = {
                @Index(name = "idx_payments_order_id", columnList = "order_id"),
                @Index(name = "idx_payments_saga_id", columnList = "saga_id"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_method_provider", columnList = "method, provider")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment extends BaseEntity {

    @Column(name = "payment_code", nullable = false, length = 50)
    String paymentCode;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    UUID orderId;

    @Column(name = "order_code", length = 50)
    String orderCode;

    @Column(name = "customer_id", columnDefinition = "BINARY(16)")
    UUID customerId;

    @Column(name = "saga_id", nullable = false, columnDefinition = "BINARY(16)")
    UUID sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    PaymentStatus status;

    @Column(name = "amount_required", nullable = false, precision = 14, scale = 2)
    BigDecimal amountRequired;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    BigDecimal amountPaid;

    @Column(name = "amount_refunded", nullable = false, precision = 14, scale = 2)
    BigDecimal amountRefunded;

    @Column(name = "currency", nullable = false, length = 10)
    String currency;

    @Column(name = "expires_at")
    LocalDateTime expiresAt;

    @Column(name = "completed_at")
    LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    LocalDateTime cancelledAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    String failureReason;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    String reviewReason;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    List<PaymentAttempt> attempts = new ArrayList<>();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    List<PaymentTransaction> transactions = new ArrayList<>();

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CashCollection> cashCollections = new ArrayList<>();

    @PrePersist
    void prePersistPayment() {
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
        if (amountPaid == null) {
            amountPaid = BigDecimal.ZERO;
        }
        if (amountRefunded == null) {
            amountRefunded = BigDecimal.ZERO;
        }
        if (currency == null) {
            currency = "VND";
        }
        if (provider == null) {
            provider = method == PaymentMethod.CASH
                    ? PaymentProvider.INTERNAL
                    : PaymentProvider.SEPAY;
        }
    }
}