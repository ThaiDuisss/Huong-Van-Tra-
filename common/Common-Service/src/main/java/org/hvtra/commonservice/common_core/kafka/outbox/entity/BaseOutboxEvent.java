package org.hvtra.commonservice.kafka.outbox.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
public abstract class BaseOutboxEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion = 1;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(name = "business_key", nullable = false, length = 150)
    private String businessKey;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "source_service", nullable = false, length = 100)
    private String sourceService;

    @Column(name = "saga_id", length = 36)
    private String sagaId;

    @Column(name = "correlation_id", length = 150)
    private String correlationId;

    @Column(name = "causation_id", length = 150)
    private String causationId;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "headers", columnDefinition = "JSON")
    private String headers;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();

        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
        if (eventVersion == null) {
            eventVersion = 1;
        }
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }
}
