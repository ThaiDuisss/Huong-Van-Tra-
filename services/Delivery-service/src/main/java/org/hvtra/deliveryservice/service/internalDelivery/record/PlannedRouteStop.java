package org.hvtra.deliveryservice.service.internalDelivery.record;

import org.hvtra.deliveryservice.model.enums.TravelEstimateSource;

import java.time.Instant;

public record PlannedRouteStop(
        RoutePlanningCandidate candidate,
        int sequenceNo,
        int distanceFromPreviousMeter,
        int travelDurationSecond,
        Instant plannedArrivalAt,
        TravelEstimateSource estimateSource

) {
}
