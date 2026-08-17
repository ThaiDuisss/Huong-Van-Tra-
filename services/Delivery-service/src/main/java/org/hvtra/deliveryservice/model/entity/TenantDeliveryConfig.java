package org.hvtra.deliveryservice.model.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Where;
import org.hibernate.type.SqlTypes;
import org.hvtra.deliveryservice.model.entity.base.SoftDeleteAuditableEntity;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.RoutePlanningAlgorithmType;
import org.hvtra.deliveryservice.model.enums.TravelEstimationMode;


import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "tenant_delivery_configs")
@Where(clause = "is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantDeliveryConfig extends SoftDeleteAuditableEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "tenant_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID tenantId;

    @Column(name = "internal_delivery_enabled", nullable = false)
    private boolean internalDeliveryEnabled;

    @Column(name = "max_delivery_distance_km", precision = 10, scale = 2)
    private BigDecimal maxDeliveryDistanceKm;

    @Column(name = "max_orders_per_route", nullable = false)
    private int maxOrdersPerRoute = 10;

    @Column(name = "max_route_weight_gram")
    private Integer maxRouteWeightGram;

    @Column(name = "max_route_cod_amount")
    private Long maxRouteCodAmount;

    @Column(name = "max_route_duration_min")
    private Integer maxRouteDurationMin;

    @Column(name = "base_shipping_fee", nullable = false)
    private long baseShippingFee;

    @Column(name = "fee_per_km", nullable = false)
    private long feePerKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_provider", length = 50)
    private DeliveryProvider fallbackProvider;

    @Column(name = "morning_cutoff_time")
    private LocalTime morningCutoffTime;

    @Column(name = "afternoon_cutoff_time")
    private LocalTime afternoonCutoffTime;

    @Column(name = "evening_cutoff_time")
    private LocalTime eveningCutoffTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "planning_algorithm", nullable = false, length = 50)
    private RoutePlanningAlgorithmType planningAlgorithm = RoutePlanningAlgorithmType.NEAREST_NEIGHBOR;

    @Column(name = "average_speed_kmh", nullable = false, precision = 5, scale = 2)
    private BigDecimal averageSpeedKmh = BigDecimal.valueOf(25);
    @Enumerated(EnumType.STRING)
    @Column(name = "travel_estimation_mode", nullable = false, length = 60)
    private TravelEstimationMode travelEstimationMode =
            TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK;

    @Column(name = "service_time_per_stop_min", nullable = false)
    private int serviceTimePerStopMin = 5;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public static TenantDeliveryConfig create(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        TenantDeliveryConfig config = new TenantDeliveryConfig();
        config.tenantId = tenantId;
        config.active = true;
        config.maxOrdersPerRoute = 10;
        config.planningAlgorithm = RoutePlanningAlgorithmType.NEAREST_NEIGHBOR;
        config.averageSpeedKmh = BigDecimal.valueOf(25);
        config.travelEstimationMode = TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK;
        config.serviceTimePerStopMin = 5;
        return config;
    }

    public void configureInternalDelivery(
            boolean enabled,
            BigDecimal maxDistanceKm,
            Integer maxWeightGram,
            Long maxCodAmount,
            Integer maxDurationMin,
            int maxOrders,
            long baseFee,
            long feePerKm,
            DeliveryProvider fallbackProvider
    ) {
        if (maxOrders <= 0) {
            throw new IllegalArgumentException("maxOrdersPerRoute must be greater than zero");
        }
        this.internalDeliveryEnabled = enabled;
        this.maxDeliveryDistanceKm = maxDistanceKm;
        this.maxRouteWeightGram = maxWeightGram;
        this.maxRouteCodAmount = maxCodAmount;
        this.maxRouteDurationMin = maxDurationMin;
        this.maxOrdersPerRoute = maxOrders;
        this.baseShippingFee = baseFee;
        this.feePerKm = feePerKm;
        this.fallbackProvider = fallbackProvider;
    }

    public boolean supportsInternalDelivery(BigDecimal distanceKm, int weightGram, long codAmount) {
        if (!active || !internalDeliveryEnabled) {
            return false;
        }
        if (maxDeliveryDistanceKm != null && distanceKm != null && distanceKm.compareTo(maxDeliveryDistanceKm) > 0) {
            return false;
        }
        if (maxRouteWeightGram != null && weightGram > maxRouteWeightGram) {
            return false;
        }
        return maxRouteCodAmount == null || codAmount <= maxRouteCodAmount;
    }

    public void configureCutoffTimes(LocalTime morning, LocalTime afternoon, LocalTime evening) {
        LocalTime resolvedMorning = morning == null ? LocalTime.of(11, 0) : morning;
        LocalTime resolvedAfternoon = afternoon == null ? LocalTime.of(16, 0) : afternoon;
        LocalTime resolvedEvening = evening == null ? LocalTime.of(20, 0) : evening;
        if (!resolvedMorning.isBefore(resolvedAfternoon)) {
            throw new IllegalArgumentException("morningCutoffTime must be before afternoonCutoffTime");
        }
        if (!resolvedAfternoon.isBefore(resolvedEvening)) {
            throw new IllegalArgumentException("afternoonCutoffTime must be before eveningCutoffTime");
        }
        this.morningCutoffTime = morning;
        this.afternoonCutoffTime = afternoon;
        this.eveningCutoffTime = evening;
    }

    public void configurePlanning(
            RoutePlanningAlgorithmType algorithm,
            BigDecimal speedKmh,
            TravelEstimationMode estimationMode,
            int serviceTimeMin
    ) {
        if (speedKmh == null || speedKmh.signum() <= 0) {
            throw new IllegalArgumentException("averageSpeedKmh must be greater than zero");
        }
        if (serviceTimeMin < 0) {
            throw new IllegalArgumentException("serviceTimePerStopMin must not be negative");
        }
        this.planningAlgorithm = algorithm == null
                ? RoutePlanningAlgorithmType.NEAREST_NEIGHBOR
                : algorithm;
        this.averageSpeedKmh = speedKmh;
        this.travelEstimationMode = estimationMode == null
                ? TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK
                : estimationMode;
        this.serviceTimePerStopMin = serviceTimeMin;
    }

    public long calculateInternalShippingFee(BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.signum() < 0) {
            throw new IllegalArgumentException("distanceKm must not be negative");
        }
        return BigDecimal.valueOf(baseShippingFee)
                .add(distanceKm.multiply(BigDecimal.valueOf(feePerKm)))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
