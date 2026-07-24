package org.hvtra.deliveryservice.service.internalDelivery.routing.google;

import java.util.List;

public record GoogleRoutesResponse(List<Route> routes) {
    public record Route(
            Integer distanceMeters,
            String duration,
            String staticDuration
    ) {
    }
}
