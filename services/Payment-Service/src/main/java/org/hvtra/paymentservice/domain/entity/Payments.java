package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_payment_code", columnNames = "payment_code")
        },
        indexes = {
                @Index(name = "idx_payments_order_id", columnList = "order_id"),
                @Index(name = "idx_payments_order_status", columnList = "order_id, status"),
                @Index(name = "idx_payments_status_expires_at", columnList = "status, expires_at"),
                @Index(name = "idx_payments_method_status", columnList = "method, status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payments extends BaseEntity {

    @Column(name = "payment_code", nullable = false, unique = true, length = 50)
    String paymentCode;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 30)
    PaymentProvider provider;

    @Column(name = "amount_vnd", nullable = false)
    Long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    PaymentStatus status;

    @Column(name = "qr_url", columnDefinition = "TEXT")
    String qrUrl;

    @Column(name = "expires_at")
    Instant expiresAt;

    @Column(name = "paid_at")
    Instant paidAt;

    @Column(name = "cancelled_at")
    Instant cancelledAt;

    @Column(name = "attempt_no", nullable = false)
    Integer attemptNo;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    String reviewReason;

    public void markSucceeded(Instant paidAt) {
        this.status = PaymentStatus.SUCCEEDED;
        this.paidAt = paidAt == null ? Instant.now() : paidAt;
        this.reviewReason = null;
    }

    public void markNeedReview(String reason) {
        this.status = PaymentStatus.NEED_REVIEW;
        this.reviewReason = reason;
    }

    public void markExpired() {
        this.status = PaymentStatus.EXPIRED;
    }

    public void markCancelled() {
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public boolean isFinalStatus() {
        return status == PaymentStatus.SUCCEEDED
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.EXPIRED
                || status == PaymentStatus.CANCELLED;
    }
}