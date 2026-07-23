package org.hvtra.deliveryservice.service.internaldelivery.planning;

import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.enums.DeliveryMode;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.PaymentType;
import org.hvtra.deliveryservice.model.enums.TravelEstimationMode;
import org.hvtra.deliveryservice.service.internalDelivery.helper.HaversineDistanceCalculator;
import org.hvtra.deliveryservice.service.internalDelivery.planning.EarliestDeadlineRoutePlanningAlgorithm;
import org.hvtra.deliveryservice.service.internalDelivery.planning.NearestNeighborRoutePlanningAlgorithm;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;
import org.hvtra.deliveryservice.service.internalDelivery.record.RoutePlanningCandidate;
import org.hvtra.deliveryservice.service.internalDelivery.record.RoutePlanningConstraints;
import org.hvtra.deliveryservice.service.internalDelivery.record.RoutePlanningOutcome;
import org.hvtra.deliveryservice.service.internalDelivery.routing.HybridRouteTravelEstimator;
import org.hvtra.deliveryservice.service.internalDelivery.routing.RouteTravelEstimator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
class RoutePlanningAlgorithmTest {

    private static final GeoPoint ORIGIN = point("21.0278000", "105.8342000");
    private static final Instant START_AT = Instant.parse("2026-07-20T01:00:00Z");
    private final HaversineDistanceCalculator distanceCalculator = new HaversineDistanceCalculator();
    private final RouteTravelEstimator travelEstimator = new HybridRouteTravelEstimator(
            distanceCalculator,
            (from, to, departureAt) -> Optional.empty()
    );

    @Test
    void nearestNeighborVisitsClosestStopFirst() {
        RoutePlanningCandidate farther = candidate(
                "SHP-FAR",
                point("21.0600000", "105.8800000"),
                Instant.parse("2026-07-20T05:00:00Z")
        );
        RoutePlanningCandidate nearer = candidate(
                "SHP-NEAR",
                point("21.0300000", "105.8380000"),
                Instant.parse("2026-07-20T05:00:00Z")
        );

        RoutePlanningOutcome outcome = new NearestNeighborRoutePlanningAlgorithm(distanceCalculator, travelEstimator)
                .plan(List.of(farther, nearer), constraints(1, 10));

        assertThat(outcome.routes()).hasSize(1);
        assertThat(outcome.routes().getFirst().stops())
                .extracting(stop -> stop.candidate().shipment().getShipmentCode())
                .containsExactly("SHP-NEAR", "SHP-FAR");
    }

    @Test
    void earliestDeadlinePrioritizesUrgentShipment() {
        RoutePlanningCandidate urgent = candidate(
                "SHP-URGENT",
                point("21.0600000", "105.8800000"),
                Instant.parse("2026-07-20T02:00:00Z")
        );
        RoutePlanningCandidate nearby = candidate(
                "SHP-NEAR",
                point("21.0300000", "105.8380000"),
                Instant.parse("2026-07-20T05:00:00Z")
        );

        RoutePlanningOutcome outcome = new EarliestDeadlineRoutePlanningAlgorithm(distanceCalculator, travelEstimator)
                .plan(List.of(nearby, urgent), constraints(1, 10));

        assertThat(outcome.routes().getFirst().stops().getFirst()
                .candidate().shipment().getShipmentCode()).isEqualTo("SHP-URGENT");
    }

    @Test
    void splitsShipmentsByOrderCapacity() {
        List<RoutePlanningCandidate> candidates = List.of(
                candidate("SHP-1", point("21.0300000", "105.8380000"), null),
                candidate("SHP-2", point("21.0350000", "105.8420000"), null),
                candidate("SHP-3", point("21.0400000", "105.8460000"), null)
        );

        RoutePlanningOutcome outcome = new NearestNeighborRoutePlanningAlgorithm(distanceCalculator, travelEstimator)
                .plan(candidates, constraints(2, 2));

        assertThat(outcome.routes()).hasSize(2);
        assertThat(outcome.routes()).extracting(route -> route.stops().size())
                .containsExactly(2, 1);
        assertThat(outcome.rejected()).isEmpty();
    }

    @Test
    void reportsCandidatesLeftAfterRouteLimit() {
        List<RoutePlanningCandidate> candidates = List.of(
                candidate("SHP-1", point("21.0300000", "105.8380000"), null),
                candidate("SHP-2", point("21.0350000", "105.8420000"), null)
        );

        RoutePlanningOutcome outcome = new NearestNeighborRoutePlanningAlgorithm(distanceCalculator, travelEstimator)
                .plan(candidates, constraints(1, 1));

        assertThat(outcome.routes()).hasSize(1);
        assertThat(outcome.rejected()).hasSize(1);
        assertThat(outcome.rejected().getFirst().reason()).isEqualTo("MAX_ROUTES_REACHED");
    }

    private RoutePlanningConstraints constraints(int maxRoutes, int maxOrders) {
        return new RoutePlanningConstraints(
                ORIGIN,
                START_AT,
                maxRoutes,
                maxOrders,
                30_000,
                10_000_000L,
                480,
                BigDecimal.valueOf(25),
                TravelEstimationMode.HAVERSINE_ONLY,
                5
        );
    }

    private RoutePlanningCandidate candidate(String code, GeoPoint destination, Instant requestedTo) {
        Shipment shipment = Shipment.create(
                UUID.randomUUID(),
                code,
                UUID.randomUUID(),
                "ORDER-" + code,
                1,
                DeliveryMode.INTERNAL,
                DeliveryProvider.STORE_COURIER,
                PaymentType.COD,
                100_000,
                0,
                UUID.randomUUID(),
                "Receiver",
                "0900000000",
                "Delivery address",
                "Pickup address"
        );
        shipment.configurePickupSnapshot(
                "Store",
                "0911111111",
                null,
                null,
                null,
                ORIGIN.latitude(),
                ORIGIN.longitude()
        );
        shipment.configureDeliveryLocation(
                null,
                null,
                null,
                destination.latitude(),
                destination.longitude()
        );
        shipment.configurePackage(1, 1_000, 10, 10, 10, 100_000);
        shipment.schedule(null, requestedTo, requestedTo);
        return new RoutePlanningCandidate(shipment, ORIGIN, destination);
    }

    private static GeoPoint point(String latitude, String longitude) {
        return new GeoPoint(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
