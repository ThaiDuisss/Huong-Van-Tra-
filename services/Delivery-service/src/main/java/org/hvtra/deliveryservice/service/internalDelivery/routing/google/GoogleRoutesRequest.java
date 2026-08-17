package org.hvtra.deliveryservice.service.internalDelivery.routing.google;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.hvtra.deliveryservice.config.GoogleRoutesProperties;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleRoutesRequest(
        Waypoint origin,
        Waypoint destination,
        String travelMode,
        String routingPreference,
        Instant departureTime,
        boolean computeAlternativeRoutes,
        RouteModifiers routeModifiers,
        String languageCode,
        String units
) {
    public static GoogleRoutesRequest create(
            GeoPoint from,
            GeoPoint to,
            Instant departureTime,
            GoogleRoutesProperties properties
    ) {
        return new GoogleRoutesRequest(
                Waypoint.from(from),
                Waypoint.from(to),
                properties.resolvedTravelMode().name(),
                properties.resolvedRoutingPreference().name(),
                departureTime,
                false,
                new RouteModifiers(
                        properties.avoidTolls(),
                        properties.avoidHighways(),
                        properties.avoidFerries()
                ),
                "vi-VN",
                "METRIC"
        );
    }

    public record Waypoint(Location location) {
        private static Waypoint from(GeoPoint point) {
            return new Waypoint(new Location(new LatLng(point.latitude(), point.longitude())));
        }
    }

    public record Location(LatLng latLng) {
    }

    public record LatLng(BigDecimal latitude, BigDecimal longitude) {
    }

    public record RouteModifiers(
            boolean avoidTolls,
            boolean avoidHighways,
            boolean avoidFerries
    ) {
    }
}
