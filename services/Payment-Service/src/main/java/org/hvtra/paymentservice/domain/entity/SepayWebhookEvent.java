//package org.hvtra.paymentservice.domain.entity;
//
//import jakarta.persistence.*;
//import lombok.*;
//import lombok.experimental.FieldDefaults;
//import org.hvtra.paymentservice.domain.enums.SepayWebhookStatus;
//
//import java.time.Instant;
//import java.util.UUID;
//
//@Entity
//@Table(
//        name = "sepay_webhook_events",
//        uniqueConstraints = {
//                @UniqueConstraint(
//                        name = "uk_sepay_webhook_transaction",
//                        columnNames = "sepay_transaction_id"
//                )
//        },
//        indexes = {
//                @Index(name = "idx_sepay_webhook_status", columnList = "status"),
//                @Index(name = "idx_sepay_webhook_received_at", columnList = "received_at")
//        }
//)
//@Getter
//@Setter
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE)
//public class SepayWebhookEvent {
//
//    @Id
//    @Column(columnDefinition = "BINARY(16)")
//    UUID id;
//
//    @Column(name = "sepay_transaction_id", nullable = false, length = 100)
//    String sepayTransactionId;
//
//    @Column(name = "signature_valid", nullable = false)
//    Boolean signatureValid;
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "status", nullable = false, length = 30)
//    SepayWebhookStatus status;
//
//    @Column(name = "raw_body", columnDefinition = "JSON")
//    String rawBody;
//
//    @Column(name = "raw_headers", columnDefinition = "JSON")
//    String rawHeaders;
//
//    @Column(name = "error_message", columnDefinition = "TEXT")
//    String errorMessage;
//
//    @Column(name = "duplicate_count", nullable = false)
//    Integer duplicateCount;
//
//    @Column(name = "received_at", nullable = false, updatable = false)
//    Instant receivedAt;
//
//    @Column(name = "last_received_at")
//    Instant lastReceivedAt;
//
//    @Column(name = "processed_at")
//    Instant processedAt;
//
//    @PrePersist
//    void prePersist() {
//        if (id == null) {
//            id = UUID.randomUUID();
//        }
//
//        if (signatureValid == null) {
//            signatureValid = false;
//        }
//
//        if (status == null) {
//            status = SepayWebhookStatus.RECEIVED;
//        }
//
//        if (duplicateCount == null) {
//            duplicateCount = 0;
//        }
//
//        receivedAt = Instant.now();
//    }
//
//    public void markProcessing() {
//        this.status = SepayWebhookStatus.PROCESSING;
//    }
//
//    public void markProcessed() {
//        this.status = SepayWebhookStatus.PROCESSED;
//        this.processedAt = Instant.now();
//    }
//
//    public void markNeedReview(String errorMessage) {
//        this.status = SepayWebhookStatus.NEED_REVIEW;
//        this.errorMessage = errorMessage;
//        this.processedAt = Instant.now();
//    }
//
//    public void markFailed(String errorMessage) {
//        this.status = SepayWebhookStatus.FAILED;
//        this.errorMessage = errorMessage;
//        this.processedAt = Instant.now();
//    }
//
//    public void markAuthFailed(String errorMessage) {
//        this.status = SepayWebhookStatus.AUTH_FAILED;
//        this.errorMessage = errorMessage;
//        this.processedAt = Instant.now();
//    }
//
//    public void increaseDuplicateCount() {
//        if (this.duplicateCount == null) {
//            this.duplicateCount = 0;
//        }
//
//        this.duplicateCount++;
//        this.lastReceivedAt = Instant.now();
//        this.status = SepayWebhookStatus.DUPLICATE;
//    }
//}package org.hvtra.paymentservice.domain.entity;
//
//import jakarta.persistence.*;
//import lombok.*;
//import lombok.experimental.FieldDefaults;
//import org.hvtra.paymentservice.domain.enums.SepayWebhookStatus;
//
//import java.time.Instant;
//import java.util.UUID;
//
//@Entity
//@Table(
//        name = "sepay_webhook_events",
//        uniqueConstraints = {
//                @UniqueConstraint(
//                        name = "uk_sepay_webhook_transaction",
//                        columnNames = "sepay_transaction_id"
//                )
//        },
//        indexes = {
//                @Index(name = "idx_sepay_webhook_status", columnList = "status"),
//                @Index(name = "idx_sepay_webhook_received_at", columnList = "received_at")
//        }
//)
//@Getter
//@Setter
//@Builder
//@NoArgsConstructor
//@AllArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE)
//public class SepayWebhookEvent {
//
//    @Id
//    @Column(columnDefinition = "BINARY(16)")
//    UUID id;
//
//    @Column(name = "sepay_transaction_id", nullable = false, length = 100)
//    String sepayTransactionId;
//
//    @Column(name = "signature_valid", nullable = false)
//    Boolean signatureValid;
//
//    @Enumerated(EnumType.STRING)
//    @Column(name = "status", nullable = false, length = 30)
//    SepayWebhookStatus status;
//
//    @Column(name = "raw_body", columnDefinition = "JSON")
//    String rawBody;
//
//    @Column(name = "raw_headers", columnDefinition = "JSON")
//    String rawHeaders;
//
//    @Column(name = "error_message", columnDefinition = "TEXT")
//    String errorMessage;
//
//    @Column(name = "duplicate_count", nullable = false)
//    Integer duplicateCount;
//
//    @Column(name = "received_at", nullable = false, updatable = false)
//    Instant receivedAt;
//
//    @Column(name = "last_received_at")
//    Instant lastReceivedAt;
//
//    @Column(name = "processed_at")
//    Instant processedAt;
//
//    @PrePersist
//    void prePersist() {
//        if (id == null) {
//            id = UUID.randomUUID();
//        }
//
//        if (signatureValid == null) {
//            signatureValid = false;
//        }
//
//        if (status == null) {
//            status = SepayWebhookStatus.RECEIVED;
//        }
//
//        if (duplicateCount == null) {
//            duplicateCount = 0;
//        }
//
//        receivedAt = Instant.now();
//    }
//
//    public void markProcessing() {
//        this.status = SepayWebhookStatus.PROCESSING;
//    }
//
//    public void markProcessed() {
//        this.status = SepayWebhookStatus.PROCESSED;
//        this.processedAt = Instant.now();
//    }
//
//    public void markNeedReview(String errorMessage) {
//        this.status = SepayWebhookStatus.NEED_REVIEW;
//        this.errorMessage = errorMessage;
//        this.processedAt = Instant.now();
//    }
//
//    public void markFailed(String errorMessage) {
//        this.status = SepayWebhookStatus.FAILED;
//        this.errorMessage = errorMessage;
//        this.processedAt = Instant.now();
//    }
//
//    public void markAuthFailed(String errorMessage) {
//        this.status = SepayWebhookStatus.AUTH_FAILED;
//        this.errorMessage = errorMessage;
//        this.processedAt = Instant.now();
//    }
//
//    public void increaseDuplicateCount() {
//        if (this.duplicateCount == null) {
//            this.duplicateCount = 0;
//        }
//
//        this.duplicateCount++;
//        this.lastReceivedAt = Instant.now();
//        this.status = SepayWebhookStatus.DUPLICATE;
//    }
//}