package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.paymentservice.domain.enums.PaymentProvider;
import org.hvtra.paymentservice.domain.enums.SepayWebhookStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "payment_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_webhook_provider_event",
                        columnNames = {"provider", "provider_event_id"}
                )
        },
        indexes = {
                @Index(name = "idx_webhook_payment_code", columnList = "payment_code"),
                @Index(name = "idx_webhook_status", columnList = "status"),
                @Index(name = "idx_webhook_matched_payment_id", columnList = "matched_payment_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentWebhookEvent {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    PaymentProvider provider;

    @Column(name = "provider_event_id", nullable = false, length = 100)
    String providerEventId;

    @Column(name = "reference_code", length = 100)
    String referenceCode;

    @Column(name = "payment_code", length = 50)
    String paymentCode;

    @Column(name = "matched_payment_id", columnDefinition = "BINARY(16)")
    UUID matchedPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    SepayWebhookStatus status;

    @Column(name = "transfer_type", length = 30)
    String transferType;

    @Column(name = "transfer_amount", precision = 14, scale = 2)
    BigDecimal transferAmount;

    @Column(name = "transaction_date")
    LocalDateTime transactionDate;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "JSON")
    String rawPayload;

    @Column(name = "processed_at")
    LocalDateTime processedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    String errorMessage;

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
            status = SepayWebhookStatus.RECEIVED;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markProcessed(UUID matchedPaymentId) {
        this.status = SepayWebhookStatus.PROCESSED;
        this.matchedPaymentId = matchedPaymentId;
        this.processedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markNeedReview(String reason, UUID matchedPaymentId) {
        this.status = SepayWebhookStatus.NEED_REVIEW;
        this.matchedPaymentId = matchedPaymentId;
        this.processedAt = LocalDateTime.now();
        this.errorMessage = reason;
    }

    public void markFailed(String reason) {
        this.status = SepayWebhookStatus.FAILED;
        this.processedAt = LocalDateTime.now();
        this.errorMessage = reason;
    }
}