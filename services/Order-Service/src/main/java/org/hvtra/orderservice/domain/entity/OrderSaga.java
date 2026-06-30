package org.hvtra.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import org.hvtra.commonservice.domain.entity.BaseEntity;
import org.hvtra.commonservice.domain.entity.BaseEntityNoId;
import org.hvtra.commonservice.kafka.saga.enums.CompensationStatus;
import org.hvtra.commonservice.kafka.saga.enums.SagaStatus;
import org.hvtra.commonservice.kafka.saga.enums.SagaStep;
import org.hvtra.orderservice.domain.enums.OrderSagaStep;

import java.util.UUID;

@Entity
@Table(
    name = "order_saga",
    indexes = {
        @Index(name = "idx_order_saga_saga_id", columnList = "saga_id"),
        @Index(name = "idx_order_saga_order_id", columnList = "order_id"),
        @Index(name = "idx_order_saga_status", columnList = "status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_saga_saga_id", columnNames = "saga_id"),
        @UniqueConstraint(name = "uk_order_saga_order_id", columnNames = "order_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSaga extends BaseEntityNoId {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID sagaId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SagaStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 80)
    private OrderSagaStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 50)
    private SagaStep paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_status", nullable = false, length = 50)
    private SagaStep inventoryStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_status", nullable = false, length = 50)
    private SagaStep promotionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "compensation_status", nullable = false, length = 50)
    private CompensationStatus compensationStatus;

    @Column(name = "last_event_id")
    private UUID lastEventId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @PrePersist
    void prePersist() {
        if (this.status == null) {
            this.status = SagaStatus.STARTED;
        }

        if (this.currentStep == null) {
            this.currentStep = OrderSagaStep.ORDER_CREATED;
        }

        if (this.paymentStatus == null) {
            this.paymentStatus = SagaStep.PENDING;
        }

        if (this.inventoryStatus == null) {
            this.inventoryStatus = SagaStep.PENDING;
        }

        if (this.promotionStatus == null) {
            this.promotionStatus = SagaStep.PENDING;
        }

        if (this.compensationStatus == null) {
            this.compensationStatus = CompensationStatus.NONE;
        }
    }

}