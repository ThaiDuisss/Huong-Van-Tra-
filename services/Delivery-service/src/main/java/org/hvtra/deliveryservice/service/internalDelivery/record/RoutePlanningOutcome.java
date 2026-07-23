package org.hvtra.deliveryservice.service.internalDelivery.record;

import java.util.List;

public record RoutePlanningOutcome(
        List<PlannedRoute> routes,
        List<RejectedPlanningCandidate> rejected
) {
    public RoutePlanningOutcome {
        routes = List.copyOf(routes);
        rejected = List.copyOf(rejected);
    }
}
