package org.hvtra.deliveryservice.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;
import org.hvtra.deliveryservice.model.entity.base.SoftDeleteAuditableEntity;
import org.hvtra.deliveryservice.model.enums.DeliveryRouteStatus;
import org.hvtra.deliveryservice.model.enums.DispatchWave;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "delivery_routes",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_routes_tenant_code", columnNames = {"tenant_id", "route_code"}))
@Where(clause = "is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryRoute extends SoftDeleteAuditableEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "tenant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID tenantId;

    @Column(name = "route_code", nullable = false, length = 50)
    private String routeCode;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "courier_employee_id", columnDefinition = "BINARY(16)")
    private UUID courierEmployeeId;

    @Column(name = "courier_name_snapshot", length = 255)
    private String courierNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_wave", length = 30)
    private DispatchWave dispatchWave;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryRouteStatus status;

    @Column(name = "planned_start_at")
    private Instant plannedStartAt;

    @Column(name = "planned_end_at")
    private Instant plannedEndAt;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Column(name = "total_stops", nullable = false)
    private int totalStops;

    @Column(name = "total_distance_meter", nullable = false)
    private int totalDistanceMeter;

    @Column(name = "total_duration_second", nullable = false)
    private int totalDurationSecond;

    @Column(name = "total_weight_gram", nullable = false)
    private int totalWeightGram;

    @Column(name = "total_cod_amount", nullable = false)
    private long totalCodAmount;

    @Column(name = "planning_algorithm", length = 50)
    private String planningAlgorithm;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private final List<RouteStop> stops = new ArrayList<>();

    public static DeliveryRoute create(UUID tenantId, String routeCode, DispatchWave wave) {
        DeliveryRoute route = new DeliveryRoute();
        route.tenantId = tenantId;
        route.routeCode = routeCode;
        route.dispatchWave = wave;
        route.status = DeliveryRouteStatus.DRAFT;
        return route;
    }

    public void plan(Instant startAt, Instant endAt, String algorithm) {
        requireStatus(DeliveryRouteStatus.DRAFT, DeliveryRouteStatus.PLANNING);
        if (startAt != null && endAt != null && endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("plannedEndAt must not be before plannedStartAt");
        }
        this.plannedStartAt = startAt;
        this.plannedEndAt = endAt;
        this.planningAlgorithm = algorithm;
        this.status = DeliveryRouteStatus.PLANNING;
    }

    public void addStop(RouteStop stop) {
        requireStatus(DeliveryRouteStatus.DRAFT, DeliveryRouteStatus.PLANNING);
        boolean duplicated = stops.stream().anyMatch(current ->
                current.getShipment() == stop.getShipment()
                        || (current.getShipment().getId() != null
                        && Objects.equals(current.getShipment().getId(), stop.getShipment().getId()))
        );
        if (duplicated) {
            throw new IllegalArgumentException("Shipment already exists in route");
        }
        stop.attachTo(this);
        this.stops.add(stop);
        recalculateTotals();
    }

    public void removeStop(UUID shipmentId) {
        requireStatus(DeliveryRouteStatus.DRAFT, DeliveryRouteStatus.PLANNING);
        stops.removeIf(stop -> Objects.equals(stop.getShipment().getId(), shipmentId));
        resequence();
        recalculateTotals();
    }

    public void assignCourier(UUID employeeId, String name) {
        requireStatus(DeliveryRouteStatus.DRAFT, DeliveryRouteStatus.PLANNING, DeliveryRouteStatus.READY);
        this.courierEmployeeId = employeeId;
        this.courierNameSnapshot = name;
        this.status = DeliveryRouteStatus.ASSIGNED;
    }

    public void markReady() {
        requireStatus(DeliveryRouteStatus.PLANNING, DeliveryRouteStatus.DRAFT);
        if (stops.isEmpty()) {
            throw new IllegalStateException("Route must contain at least one stop");
        }
        this.status = DeliveryRouteStatus.READY;
    }

    public void start(Instant startedAt) {
        requireStatus(DeliveryRouteStatus.READY, DeliveryRouteStatus.ASSIGNED);
        if (courierEmployeeId == null) {
            throw new IllegalStateException("Courier must be assigned before starting route");
        }
        this.actualStartAt = startedAt;
        this.status = DeliveryRouteStatus.STARTED;
    }

    public void complete(Instant completedAt) {
        requireStatus(DeliveryRouteStatus.STARTED);
        boolean unfinished = stops.stream().anyMatch(stop -> !stop.isTerminal());
        if (unfinished) {
            throw new IllegalStateException("All route stops must be terminal before completing route");
        }
        this.actualEndAt = completedAt;
        this.status = DeliveryRouteStatus.COMPLETED;
    }

    public void cancel() {
        if (status == DeliveryRouteStatus.STARTED || status == DeliveryRouteStatus.COMPLETED) {
            throw new IllegalStateException("Started or completed route cannot be cancelled");
        }
        this.status = DeliveryRouteStatus.CANCELLED;
    }

    public void updateMetrics(int distanceMeter, int durationSecond) {
        this.totalDistanceMeter = Math.max(distanceMeter, 0);
        this.totalDurationSecond = Math.max(durationSecond, 0);
    }

    private void resequence() {
        stops.sort(Comparator.comparingInt(RouteStop::getSequenceNo));
        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).changeSequence(i + 1);
        }
    }

    private void recalculateTotals() {
        this.totalStops = stops.size();
        this.totalWeightGram = stops.stream().mapToInt(stop -> stop.getShipment().getTotalWeightGram()).sum();
        this.totalCodAmount = stops.stream().mapToLong(stop -> stop.getShipment().getCodAmount()).sum();
    }

    private void requireStatus(DeliveryRouteStatus... allowedStatuses) {
        for (DeliveryRouteStatus allowed : allowedStatuses) {
            if (status == allowed) {
                return;
            }
        }
        throw new IllegalStateException("Invalid route status transition from " + status);
    }
}
