package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.paymentservice.domain.enums.PaymentAttemptStatus;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "payment_attempts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_attempt_no", columnNames = {"payment_id", "attempt_no"}),
                @UniqueConstraint(name = "uk_payment_attempt_transfer_content", columnNames = "transfer_content")
        },
        indexes = {
                @Index(name = "idx_payment_attempts_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_attempts_status", columnList = "status"),
                @Index(name = "idx_payment_attempts_provider_ref", columnList = "provider_reference")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentAttempt {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_attempts_payment")
    )
    Payment payment;

    @Column(name = "attempt_no", nullable = false)
    Integer attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    PaymentAttemptStatus status;

    @Column(name = "amount_expected", nullable = false, precision = 14, scale = 2)
    BigDecimal amountExpected;

    @Column(name = "currency", nullable = false, length = 10)
    String currency;

    // SePay/Bank transfer
    @Column(name = "transfer_content", length = 100)
    String transferContent;

    @Column(name = "qr_url", columnDefinition = "TEXT")
    String qrUrl;

    @Column(name = "bank_code", length = 50)
    String bankCode;

    @Column(name = "bank_account_number", length = 50)
    String bankAccountNumber;

    @Column(name = "bank_account_name", length = 255)
    String bankAccountName;

    @Column(name = "provider_request_id", length = 100)
    String providerRequestId;

    @Column(name = "provider_reference", length = 100)
    String providerReference;

    @Column(name = "expires_at")
    LocalDateTime expiresAt;

    @Column(name = "completed_at")
    LocalDateTime completedAt;

    @Column(name = "metadata", columnDefinition = "JSON")
    String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = PaymentAttemptStatus.CREATED;
        }
        if (currency == null) {
            currency = "VND";
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}