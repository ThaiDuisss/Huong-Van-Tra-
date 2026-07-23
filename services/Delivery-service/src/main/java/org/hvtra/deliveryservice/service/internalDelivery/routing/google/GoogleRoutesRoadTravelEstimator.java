package org.hvtra.deliveryservice.service.internalDelivery.routing.google;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.hvtra.deliveryservice.client.GoogleRoutesClient;
import org.hvtra.deliveryservice.config.GoogleRoutesProperties;
import org.hvtra.deliveryservice.model.enums.TravelEstimateSource;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;
import org.hvtra.deliveryservice.service.internalDelivery.routing.RoadTravelEstimator;
import org.hvtra.deliveryservice.service.internalDelivery.routing.TravelEstimate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class GoogleRoutesRoadTravelEstimator implements RoadTravelEstimator {
    private static final Logger log = LoggerFactory.getLogger(GoogleRoutesRoadTravelEstimator.class);
    private static final String FIELD_MASK = "routes.distanceMeters,routes.duration,routes.staticDuration";

    private final GoogleRoutesClient client;
    private final GoogleRoutesProperties properties;

    private final AtomicLong unavailableUntilEpochMilli = new AtomicLong();
    @Override
    public Optional<TravelEstimate> estimate(GeoPoint from, GeoPoint to, Instant departureAt) {
        long now = System.currentTimeMillis();

        if (!properties.configured() || now < unavailableUntilEpochMilli.get()) {
            return Optional.empty();
        }

        try {
            Instant requestDepartureAt = departureAt != null && departureAt.isAfter(Instant.now())
                    ? departureAt
                    : null;
            GoogleRoutesResponse response = client.computeRoutes(
                    properties.apiKey(),
                    FIELD_MASK,
                    GoogleRoutesRequest.create(from, to, requestDepartureAt, properties)
            );
            GoogleRoutesResponse.Route route = requireRoute(response);
            TravelEstimate estimate = new TravelEstimate(
                    requireDistance(route.distanceMeters()),
                    GoogleProtobufDurationParser.parseSecond(route.duration()),
                    TravelEstimateSource.GOOGLE_ROUTES
            );
            unavailableUntilEpochMilli.set(0);
            return Optional.of(estimate);
        } catch (FeignException exception) {
            suspendTemporarily();
            log.warn("Google Routes estimate failed with HTTP status {}; using Haversine fallback",
                    exception.status());
            return Optional.empty();
        } catch (RuntimeException exception) {
            suspendTemporarily();
            log.warn("Google Routes estimate failed with {}; using Haversine fallback",
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private GoogleRoutesResponse.Route requireRoute(GoogleRoutesResponse response) {
        if (response == null || response.routes() == null) {
            throw new IllegalStateException("Google Routes response does not contain a route");
        }
        return response.routes().stream()
                .filter(route -> route != null && route.distanceMeters() != null && route.duration() != null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Google Routes response does not contain a usable route"));
    }

    private int requireDistance(Integer distanceMeter) {
        if (distanceMeter == null || distanceMeter < 0) {
            throw new IllegalStateException("Google Routes distance is invalid");
        }
        return distanceMeter;
    }
    private void suspendTemporarily() {
        unavailableUntilEpochMilli.set(System.currentTimeMillis()
                + properties.resolvedFailureCooldownSeconds() * 1_000L);
    }
}
