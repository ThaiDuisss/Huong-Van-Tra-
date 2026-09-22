package org.hvtra.commonservice.common_core.kafka.saga.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;
import org.hvtra.commonservice.common_core.kafka.saga.enums.SagaStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Base saga dùng chung.
 *
 * Service orchestrator sẽ extends class này.
 *
 * Ví dụ:
 * - OrderSaga extends BaseSaga
 * - PaymentSaga extends BaseSaga
 */
@Getter
@Setter
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public abstract class BaseSaga {

    @Id
    @UuidGenerator
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    /**
     * Loại saga.
     *
     * Ví dụ:
     * - ORDER_PAYMENT_SAGA
     * - ORDER_CANCEL_SAGA
     */
    @Column(name = "saga_type", nullable = false, length = 100)
    String sagaType;

    /**
     * Entity chính mà saga xử lý.
     *
     * Ví dụ:
     * entityType = ORDER
     * entityId = orderId
     */
    @Column(name = "entity_type", nullable = false, length = 100)
    String entityType;

    @Column(name = "entity_id", nullable = false)
    UUID entityId;

    /**
     * Step hiện tại.
     *
     * Ví dụ:
     * - RESERVE_INVENTORY
     * - CREATE_PAYMENT
     * - CONFIRM_ORDER
     */
    @Column(name = "current_step", length = 100)
    String currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    SagaStatus status;

    /**
     * Data/context của saga dạng JSON.
     *
     * Không để common biết business field.
     */
    @Lob
    @Column(name = "saga_data_json", columnDefinition = "LONGTEXT")
    String sagaDataJson;

    @Column(name = "correlation_id", length = 150)
    String correlationId;

    @Column(name = "retry_count", nullable = false)
    Integer retryCount;

    @Column(name = "max_retry", nullable = false)
    Integer maxRetry;

    @Column(name = "started_at", nullable = false, updatable = false)
    Instant startedAt;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "failed_at")
    Instant failedAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Lob
    @Column(name = "last_error", columnDefinition = "TEXT")
    String lastError;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();

        if (status == null) {
            status = SagaStatus.STARTED;
        }

        if (retryCount == null) {
            retryCount = 0;
        }

        if (maxRetry == null) {
            maxRetry = 3;
        }

        if (startedAt == null) {
            startedAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }
}