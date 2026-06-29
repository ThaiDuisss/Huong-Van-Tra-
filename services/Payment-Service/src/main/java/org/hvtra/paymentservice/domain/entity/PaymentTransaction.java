package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.paymentservice.domain.enums.PaymentMethod;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionStatus;
import org.hvtra.paymentservice.domain.enums.PaymentTransactionType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payment_transactions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_tx_provider_tx",
                        columnNames = {"provider", "provider_transaction_id"}
                )
        },
        indexes = {
                @Index(name = "idx_payment_tx_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_tx_payment_code", columnList = "payment_code"),
                @Index(name = "idx_payment_tx_method_status", columnList = "method, status"),
                @Index(name = "idx_payment_tx_created_at", columnList = "created_at")
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

    @Column(name = "payment_id", columnDefinition = "BINARY(16)")
    UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    PaymentMethod method;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "provider", length = 30)
//    PaymentProvider provider;
//
//    @Column(name = "provider_transaction_id", length = 100)
//    String providerTransactionId;

//    @Column(name = "payment_code", length = 50)
//    String paymentCode;
//
//    @Column(name = "reference_code", length = 100)
//    String referenceCode;

//    @Column(name = "gateway", length = 100)
//    String gateway;

//    @Column(name = "account_number", length = 100)
//    String accountNumber;
//
//    @Column(name = "sub_account", length = 100)
//    String subAccount;
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "transaction_type", nullable = false, length = 30)
//    PaymentTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    PaymentTransactionStatus status;
//
//    @Column(name = "amount_vnd", nullable = false)
//    Long amountVnd;

//    @Column(name = "transaction_date")
//    Instant transactionDate;
//
//
//    @Column(name = "description", columnDefinition = "TEXT")
//    String description;
//
//    @Column(name = "review_reason", columnDefinition = "TEXT")
//    String reviewReason;
//
//    @Column(name = "raw_payload", columnDefinition = "JSON")
//    String rawPayload;
//
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
//
//        if (transactionType == null) {
//            transactionType = PaymentTransactionType.PAYMENT;
//        }

        if (status == null) {
            status = PaymentTransactionStatus.RECEIVED;
        }

        createdAt = Instant.now();
    }

    public void markMatched(UUID paymentId) {
        this.paymentId = paymentId;
        this.status = PaymentTransactionStatus.MATCHED;
//        this.reviewReason = null;
    }

    public void markNeedReview(String reason) {
        this.status = PaymentTransactionStatus.NEED_REVIEW;
//        this.reviewReason = reason;
    }


    public void markUnmatched(String reason) {
        this.status = PaymentTransactionStatus.UNMATCHED;
//        this.reviewReason = reason;
    }
}