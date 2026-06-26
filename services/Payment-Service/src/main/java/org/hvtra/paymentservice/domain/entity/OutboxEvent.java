package org.hvtra.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hvtra.paymentservice.domain.enums.OutboxStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_outbox_locked_at", columnList = "locked_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    String aggregateType;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "BINARY(16)")
    UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    Integer retryCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    String lastError;

    @Column(name = "topic", length = 255)
    String topic;

    @Column(name = "partition_no")
    Integer partitionNo;

    @Column(name = "offset_no")
    Long offsetNo;

    @Column(name = "locked_at")
    Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    String lockedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "published_at")
    Instant publishedAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (status == null) {
            status = OutboxStatus.PENDING;
        }

        if (retryCount == null) {
            retryCount = 0;
        }

        if (version == null) {
            version = 0L;
        }

        createdAt = Instant.now();
    }

    public void markProcessing(String lockedBy) {
        this.status = OutboxStatus.PROCESSING;
        this.lockedAt = Instant.now();
        this.lockedBy = lockedBy;
    }

    public void markSent(String topic, Integer partitionNo, Long offsetNo) {
        this.status = OutboxStatus.SENT;
        this.topic = topic;
        this.partitionNo = partitionNo;
        this.offsetNo = offsetNo;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String errorMessage, int maxRetry) {
        if (this.retryCount == null) {
            this.retryCount = 0;
        }

        this.retryCount++;
        this.lastError = errorMessage;

        if (this.retryCount >= maxRetry) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.status = OutboxStatus.PENDING;
        }

        this.lockedAt = null;
        this.lockedBy = null;
    }
}