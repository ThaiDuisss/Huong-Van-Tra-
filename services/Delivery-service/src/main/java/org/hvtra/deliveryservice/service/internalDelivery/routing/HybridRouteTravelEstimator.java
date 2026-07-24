package org.hvtra.deliveryservice.service.internalDelivery.routing;

import lombok.RequiredArgsConstructor;
import org.hvtra.deliveryservice.model.enums.TravelEstimateSource;
import org.hvtra.deliveryservice.model.enums.TravelEstimationMode;
import org.hvtra.deliveryservice.service.internalDelivery.helper.HaversineDistanceCalculator;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class HybridRouteTravelEstimator implements RouteTravelEstimator{

    private final HaversineDistanceCalculator haversineDistanceCalculator;
    private final RoadTravelEstimator roadTravelEstimator;

    @Override
    public TravelEstimate estimate(GeoPoint from, GeoPoint to, Instant departureAt, BigDecimal fallbackAverageSpeedKmh, TravelEstimationMode mode) {
        int haversineDistanceMeter = haversineDistanceCalculator.distanceMeter(from, to);
        TravelEstimationMode resolvedMode = mode == null
                ? TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK
                : mode;
        if (resolvedMode == TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK) {
            TravelEstimate roadEstimate = roadTravelEstimator.estimate(from, to, departureAt).orElse(null);
            if (roadEstimate != null && isPlausible(roadEstimate, haversineDistanceMeter)) {
                return roadEstimate;
            }
        }
        return new TravelEstimate(
                haversineDistanceMeter,
                fallbackDurationSecond(haversineDistanceMeter, fallbackAverageSpeedKmh),
                TravelEstimateSource.HAVERSINE
        );
    }

    private boolean isPlausible(TravelEstimate estimate, int haversineDistanceMeter) {
        int toleranceMeter = Math.max(100, haversineDistanceMeter / 20);
        return (estimate.distanceMeter() == 0 || estimate.durationSecond() > 0)
                && (long) estimate.distanceMeter() + toleranceMeter >= haversineDistanceMeter;
    }

    private int fallbackDurationSecond(int distanceMeter, BigDecimal speedKmh) {
        if (speedKmh == null || speedKmh.signum() <= 0) {
            throw new IllegalArgumentException("fallbackAverageSpeedKmh must be greater than zero");
        }
        return BigDecimal.valueOf(distanceMeter)
                .multiply(BigDecimal.valueOf(3.6))
                .divide(speedKmh, 0, RoundingMode.CEILING)
                .intValueExact();
    }
}
