package org.hvtra.commonservice.kafka.inbox;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Base inbox dùng chung.
 *
 * Mục đích:
 * - idempotency
 * - audit message đã consume
 * - retry khi xử lý message lỗi
 */
@Getter
@Setter
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public abstract class BaseInbox {

    @Id
    @UuidGenerator
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    /**
     * Event id từ producer.
     */
    @Column(name = "event_id", nullable = false)
    UUID eventId;

    /**
     * Consumer group xử lý message này.
     */
    @Column(name = "consumer_group", nullable = false, length = 150)
    String consumerGroup;

    @Column(name = "topic", nullable = false, length = 150)
    String topic;

    @Column(name = "partition_no")
    Integer partitionNo;

    @Column(name = "offset_no")
    Long offsetNo;

    @Column(name = "message_key", length = 150)
    String messageKey;

    @Column(name = "event_type", nullable = false, length = 100)
    String eventType;

    @Column(name = "event_version", nullable = false)
    Integer eventVersion;

    @Column(name = "source_service", length = 100)
    String sourceService;

    @Column(name = "entity_type", length = 100)
    String entityType;

    @Column(name = "entity_id")
    UUID entityId;

    @Column(name = "saga_id")
    UUID sagaId;

    @Column(name = "correlation_id", length = 150)
    String correlationId;

    @Lob
    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    String payloadJson;

    @Lob
    @Column(name = "headers_json", columnDefinition = "TEXT")
    String headersJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    InboxStatus status;

    @Column(name = "retry_count", nullable = false)
    Integer retryCount;

    @Column(name = "max_retry", nullable = false)
    Integer maxRetry;

    @Column(name = "received_at", nullable = false, updatable = false)
    Instant receivedAt;

    @Column(name = "processed_at")
    Instant processedAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Lob
    @Column(name = "last_error", columnDefinition = "TEXT")
    String lastError;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = InboxStatus.RECEIVED;
        }

        if (retryCount == null) {
            retryCount = 0;
        }

        if (maxRetry == null) {
            maxRetry = 3;
        }

        if (receivedAt == null) {
            receivedAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }
}