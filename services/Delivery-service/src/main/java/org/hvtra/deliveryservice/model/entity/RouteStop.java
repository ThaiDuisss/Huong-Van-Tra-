package org.hvtra.deliveryservice.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hvtra.deliveryservice.model.entity.base.AuditableEntity;
import org.hvtra.deliveryservice.model.enums.RouteStopStatus;
import org.hvtra.deliveryservice.model.enums.RouteStopType;

import java.time.Instant;

@Getter
@Entity
@Table(name = "route_stops",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_route_stops_sequence", columnNames = {"route_id", "sequence_no"}),
                @UniqueConstraint(name = "uk_route_stops_route_shipment", columnNames = {"route_id", "shipment_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteStop extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false, foreignKey = @ForeignKey(name = "fk_route_stops_route"))
    private DeliveryRoute route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_route_stops_shipment"))
    private Shipment shipment;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 30)
    private RouteStopType stopType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RouteStopStatus status;

    @Column(name = "planned_arrival_at")
    private Instant plannedArrivalAt;

    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "estimated_distance_meter")
    private Integer estimatedDistanceMeter;

    @Column(name = "estimated_duration_second")
    private Integer estimatedDurationSecond;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    public static RouteStop delivery(Shipment shipment, int sequenceNo, Instant plannedArrivalAt) {
        if (sequenceNo <= 0) {
            throw new IllegalArgumentException("sequenceNo must be greater than zero");
        }
        RouteStop stop = new RouteStop();
        stop.shipment = shipment;
        stop.sequenceNo = sequenceNo;
        stop.stopType = RouteStopType.DELIVERY;
        stop.status = RouteStopStatus.PENDING;
        stop.plannedArrivalAt = plannedArrivalAt;
        return stop;
    }

    void attachTo(DeliveryRoute route) {
        this.route = route;
    }

    void changeSequence(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public void configureEstimate(Integer distanceMeter, Integer durationSecond, Instant arrivalAt) {
        this.estimatedDistanceMeter = distanceMeter;
        this.estimatedDurationSecond = durationSecond;
        this.plannedArrivalAt = arrivalAt;
    }

    public void markArriving() {
        requireStatus(RouteStopStatus.PENDING);
        this.status = RouteStopStatus.ARRIVING;
    }

    public void markArrived(Instant arrivedAt) {
        requireStatus(RouteStopStatus.PENDING, RouteStopStatus.ARRIVING);
        this.actualArrivalAt = arrivedAt;
        this.status = RouteStopStatus.ARRIVED;
    }

    public void markDelivered(Instant completedAt) {
        requireStatus(RouteStopStatus.ARRIVED, RouteStopStatus.ARRIVING);
        this.completedAt = completedAt;
        this.status = RouteStopStatus.DELIVERED;
    }

    public void markFailed(String code, String reason, Instant completedAt) {
        requireStatus(RouteStopStatus.PENDING, RouteStopStatus.ARRIVING, RouteStopStatus.ARRIVED);
        this.failureCode = code;
        this.failureReason = reason;
        this.completedAt = completedAt;
        this.status = RouteStopStatus.FAILED;
    }

    public void skip(String reason) {
        requireStatus(RouteStopStatus.PENDING);
        this.failureReason = reason;
        this.status = RouteStopStatus.SKIPPED;
    }

    public boolean isTerminal() {
        return status == RouteStopStatus.DELIVERED || status == RouteStopStatus.FAILED || status == RouteStopStatus.SKIPPED;
    }

    private void requireStatus(RouteStopStatus... allowedStatuses) {
        for (RouteStopStatus allowed : allowedStatuses) {
            if (status == allowed) {
                return;
            }
        }
        throw new IllegalStateException("Invalid route stop status transition from " + status);
    }
}
