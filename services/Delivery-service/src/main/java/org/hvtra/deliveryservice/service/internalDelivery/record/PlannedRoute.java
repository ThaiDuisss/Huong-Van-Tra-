package org.hvtra.deliveryservice.service.internalDelivery.record;

import java.util.List;

public record PlannedRoute(
        List<PlannedRouteStop> stops,
        int totalDistanceMeter,
        int totalDurationSecond,
        int totalWeightGram,
        long totalCodAmount
) {
    public PlannedRoute {
        stops = List.copyOf(stops);
    }
}
