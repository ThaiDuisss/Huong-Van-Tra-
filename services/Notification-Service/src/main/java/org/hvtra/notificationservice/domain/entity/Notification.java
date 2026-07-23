package org.hvtra.notificationservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notifications_source_event", columnNames = "source_event_id")
        },
        indexes = {
                @Index(name = "idx_notifications_user_status", columnList = "user_id, status, created_at"),
                @Index(name = "idx_notifications_customer_status", columnList = "customer_id, status, created_at"),
                @Index(name = "idx_notifications_order_id", columnList = "order_id"),
                @Index(name = "idx_notifications_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @UuidGenerator
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "source_event_id", nullable = false, length = 150)
    private String sourceEventId;

    @Column(name = "source_event_type", nullable = false, length = 100)
    private String sourceEventType;

    @Column(name = "aggregate_type", length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 36)
    private String aggregateId;

    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "order_code", length = 50)
    private String orderCode;

    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Column(name = "saga_id", length = 36)
    private String sagaId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = "UNREAD";
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }
}
