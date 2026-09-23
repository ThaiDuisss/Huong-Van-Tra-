package org.hvtra.commonservice.common_core.kafka.saga.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;
import org.hvtra.commonservice.common_core.kafka.saga.enums.SagaStepStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Base saga step dùng để lưu từng bước của saga.
 *
 * Không bắt buộc service nào cũng phải dùng.
 * Nhưng với orchestration phức tạp thì nên có.
 */
@Getter
@Setter
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public abstract class BaseSagaStep {

    @Id
    @UuidGenerator
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    @Column(name = "saga_id", nullable = false)
    UUID sagaId;

    /**
     * Thứ tự step.
     */
    @Column(name = "step_order", nullable = false)
    Integer stepOrder;

    /**
     * Tên step.
     *
     * Ví dụ:
     * - RESERVE_INVENTORY
     * - CREATE_PAYMENT
     */
    @Column(name = "step_name", nullable = false, length = 100)
    String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    SagaStepStatus status;

    /**
     * Event dùng để compensate nếu có.
     */
    @Column(name = "compensation_event_id")
    UUID compensationEventId;

    @Column(name = "started_at")
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
            status = SagaStepStatus.PENDING;
        }

        updatedAt = now;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }
}