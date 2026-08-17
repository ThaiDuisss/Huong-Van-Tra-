package org.hvtra.deliveryservice.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hvtra.deliveryservice.model.entity.base.AuditableEntity;
import org.hvtra.deliveryservice.model.enums.DeliveryAttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "delivery_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_attempts_number", columnNames = {"shipment_id", "attempt_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAttempt extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_delivery_attempts_shipment"))
    private Shipment shipment;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "courier_employee_id", columnDefinition = "BINARY(16)")
    private UUID courierEmployeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", foreignKey = @ForeignKey(name = "fk_delivery_attempts_route"))
    private DeliveryRoute route;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryAttemptStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "collected_cod_amount", nullable = false)
    private long collectedCodAmount;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "receiver_name", length = 255)
    private String receiverName;

    @Column(name = "receiver_relationship", length = 100)
    private String receiverRelationship;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "note", length = 1000)
    private String note;

    public static DeliveryAttempt start(Shipment shipment, DeliveryRoute route, UUID courierEmployeeId, int attemptNo, Instant startedAt) {
        if (attemptNo <= 0) {
            throw new IllegalArgumentException("attemptNo must be greater than zero");
        }
        DeliveryAttempt attempt = new DeliveryAttempt();
        attempt.shipment = shipment;
        attempt.route = route;
        attempt.courierEmployeeId = courierEmployeeId;
        attempt.attemptNo = attemptNo;
        attempt.startedAt = startedAt;
        attempt.status = DeliveryAttemptStatus.STARTED;
        return attempt;
    }

    public void succeed(
            long collectedCodAmount,
            String receiverName,
            String receiverRelationship,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant finishedAt
    ) {
        requireStarted();
        this.collectedCodAmount = collectedCodAmount;
        this.receiverName = receiverName;
        this.receiverRelationship = receiverRelationship;
        this.latitude = latitude;
        this.longitude = longitude;
        this.finishedAt = finishedAt;
        this.status = DeliveryAttemptStatus.SUCCEEDED;
    }

    public void fail(DeliveryAttemptStatus failureStatus, String code, String reason, String note, Instant finishedAt) {
        requireStarted();
        if (failureStatus == DeliveryAttemptStatus.STARTED || failureStatus == DeliveryAttemptStatus.SUCCEEDED) {
            throw new IllegalArgumentException("Invalid failure status");
        }
        this.failureCode = code;
        this.failureReason = reason;
        this.note = note;
        this.finishedAt = finishedAt;
        this.status = failureStatus;
    }

    private void requireStarted() {
        if (status != DeliveryAttemptStatus.STARTED) {
            throw new IllegalStateException("Delivery attempt is already completed");
        }
    }
}
