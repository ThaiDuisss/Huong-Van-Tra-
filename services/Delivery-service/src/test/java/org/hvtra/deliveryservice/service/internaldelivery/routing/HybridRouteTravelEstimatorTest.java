package org.hvtra.deliveryservice.service.internaldelivery.routing;

import org.hvtra.deliveryservice.model.enums.TravelEstimateSource;
import org.hvtra.deliveryservice.model.enums.TravelEstimationMode;
import org.hvtra.deliveryservice.service.internalDelivery.helper.HaversineDistanceCalculator;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;
import org.hvtra.deliveryservice.service.internalDelivery.routing.HybridRouteTravelEstimator;
import org.hvtra.deliveryservice.service.internalDelivery.routing.RouteTravelEstimator;
import org.hvtra.deliveryservice.service.internalDelivery.routing.TravelEstimate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
class HybridRouteTravelEstimatorTest {

    private static final GeoPoint FROM = point("21.0278", "105.8342");
    private static final GeoPoint TO = point("21.0285", "105.8410");
    private static final Instant DEPARTURE_AT = Instant.parse("2026-07-20T01:00:00Z");
    private final HaversineDistanceCalculator haversine = new HaversineDistanceCalculator();

    @Test
    void usesRoadDistanceAndTrafficDurationWhenGoogleReturnsAValidRoute() {
        RouteTravelEstimator estimator = new HybridRouteTravelEstimator(
                haversine,
                (from, to, departureAt) -> Optional.of(
                        new TravelEstimate(1_250, 420, TravelEstimateSource.GOOGLE_ROUTES)
                )
        );

        TravelEstimate estimate = estimator.estimate(
                FROM,
                TO,
                DEPARTURE_AT,
                BigDecimal.valueOf(25),
                TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK
        );

        assertThat(estimate.distanceMeter()).isEqualTo(1_250);
        assertThat(estimate.durationSecond()).isEqualTo(420);
        assertThat(estimate.source()).isEqualTo(TravelEstimateSource.GOOGLE_ROUTES);
    }

    @Test
    void fallsBackToHaversineAndAverageSpeedWhenRoadApiIsUnavailable() {
        RouteTravelEstimator estimator = new HybridRouteTravelEstimator(
                haversine,
                (from, to, departureAt) -> Optional.empty()
        );

        TravelEstimate estimate = estimator.estimate(
                FROM,
                TO,
                DEPARTURE_AT,
                BigDecimal.valueOf(25),
                TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK
        );

        assertThat(estimate.distanceMeter()).isEqualTo(haversine.distanceMeter(FROM, TO));
        assertThat(estimate.durationSecond()).isPositive();
        assertThat(estimate.source()).isEqualTo(TravelEstimateSource.HAVERSINE);
    }

    @Test
    void doesNotCallRoadApiInHaversineOnlyMode() {
        AtomicBoolean called = new AtomicBoolean();
        RouteTravelEstimator estimator = new HybridRouteTravelEstimator(
                haversine,
                (from, to, departureAt) -> {
                    called.set(true);
                    return Optional.of(new TravelEstimate(1_250, 420, TravelEstimateSource.GOOGLE_ROUTES));
                }
        );

        TravelEstimate estimate = estimator.estimate(
                FROM,
                TO,
                DEPARTURE_AT,
                BigDecimal.valueOf(25),
                TravelEstimationMode.HAVERSINE_ONLY
        );

        assertThat(called.get()).isFalse();
        assertThat(estimate.source()).isEqualTo(TravelEstimateSource.HAVERSINE);
    }

    @Test
    void rejectsRoadResultThatIsImpossibleComparedWithHaversine() {
        RouteTravelEstimator estimator = new HybridRouteTravelEstimator(
                haversine,
                (from, to, departureAt) -> Optional.of(
                        new TravelEstimate(10, 1, TravelEstimateSource.GOOGLE_ROUTES)
                )
        );

        TravelEstimate estimate = estimator.estimate(
                FROM,
                TO,
                DEPARTURE_AT,
                BigDecimal.valueOf(25),
                TravelEstimationMode.ROAD_TRAFFIC_WITH_HAVERSINE_FALLBACK
        );

        assertThat(estimate.source()).isEqualTo(TravelEstimateSource.HAVERSINE);
    }

    private static GeoPoint point(String latitude, String longitude) {
        return new GeoPoint(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
