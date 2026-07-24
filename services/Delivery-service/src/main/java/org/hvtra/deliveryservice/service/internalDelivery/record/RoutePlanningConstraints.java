package org.hvtra.deliveryservice.service.internalDelivery.record;

import org.hvtra.deliveryservice.model.enums.TravelEstimationMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record RoutePlanningConstraints(
        GeoPoint origin,
        Instant routeStartAt,
        int maxRoutes,
        int maxOrdersPerRoute,
        Integer maxRouteWeightGram,
        Long maxRouteCodAmount,
        Integer maxRouteDurationMin,
        BigDecimal averageSpeedKmh,
        TravelEstimationMode travelEstimationMode,
        int serviceTimePerStopMin
) {
    public RoutePlanningConstraints {
        Objects.requireNonNull(origin, "origin is required");
        Objects.requireNonNull(routeStartAt, "routeStartAt is required");
        Objects.requireNonNull(averageSpeedKmh, "averageSpeedKmh is required");
        if (maxRoutes <= 0 || maxOrdersPerRoute <= 0) {
            throw new IllegalArgumentException("Route and order limits must be greater than zero");
        }
        if (maxRouteWeightGram != null && maxRouteWeightGram <= 0) {
            throw new IllegalArgumentException("maxRouteWeightGram must be greater than zero");
        }
        if (maxRouteCodAmount != null && maxRouteCodAmount < 0) {
            throw new IllegalArgumentException("maxRouteCodAmount must not be negative");
        }
        if (maxRouteDurationMin != null && maxRouteDurationMin <= 0) {
            throw new IllegalArgumentException("maxRouteDurationMin must be greater than zero");
        }
        if (averageSpeedKmh.signum() <= 0 || serviceTimePerStopMin < 0) {
            throw new IllegalArgumentException("Invalid route time estimation settings");
        }
    }
}
