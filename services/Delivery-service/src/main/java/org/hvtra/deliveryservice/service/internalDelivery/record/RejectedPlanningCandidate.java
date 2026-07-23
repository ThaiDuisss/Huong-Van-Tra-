package org.hvtra.deliveryservice.service.internalDelivery.record;

public record RejectedPlanningCandidate(
        RoutePlanningCandidate candidate,
        String reason
) {
}
