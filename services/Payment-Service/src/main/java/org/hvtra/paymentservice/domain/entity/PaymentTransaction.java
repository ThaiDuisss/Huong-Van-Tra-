package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionStatus;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "payment_transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provider_external_txn",
                        columnNames = {"provider", "external_transaction_id"}
                )
        },
        indexes = {
                @Index(name = "idx_payment_transactions_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_transactions_attempt_id", columnList = "attempt_id"),
                @Index(name = "idx_payment_transactions_status", columnList = "status"),
                @Index(name = "idx_payment_transactions_reference_code", columnList = "reference_code"),
                @Index(name = "idx_payment_transactions_paid_at", columnList = "paid_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentTransaction {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_transactions_payment")
    )
    Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "attempt_id",
            foreignKey = @ForeignKey(name = "fk_payment_transactions_attempt")
    )
    PaymentAttempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    PaymentTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    PaymentTransactionStatus status;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    String currency;

    @Column(name = "external_transaction_id", length = 100)
    String externalTransactionId;

    @Column(name = "reference_code", length = 100)
    String referenceCode;

    @Column(name = "gateway", length = 100)
    String gateway;

    @Column(name = "bank_account_number", length = 50)
    String bankAccountNumber;

    @Column(name = "counter_account_number", length = 50)
    String counterAccountNumber;

    @Column(name = "counter_account_name", length = 255)
    String counterAccountName;

    @Column(name = "transfer_content", columnDefinition = "TEXT")
    String transferContent;

    @Column(name = "paid_at")
    LocalDateTime paidAt;

    @Column(name = "raw_payload", columnDefinition = "JSON")
    String rawPayload;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    String reviewReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (transactionType == null) {
            transactionType = PaymentTransactionType.PAYMENT;
        }
        if (status == null) {
            status = PaymentTransactionStatus.RECEIVED;
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

    public void markMatched(Payment payment, PaymentAttempt attempt) {
        this.payment = payment;
        this.attempt = attempt;
        this.status = PaymentTransactionStatus.MATCHED;
        this.reviewReason = null;
    }

    public void markNeedReview(String reason) {
        this.status = PaymentTransactionStatus.NEED_REVIEW;
        this.reviewReason = reason;
    }

    public void markUnmatched(String reason) {
        this.status = PaymentTransactionStatus.UNMATCHED;
        this.reviewReason = reason;
    }
}