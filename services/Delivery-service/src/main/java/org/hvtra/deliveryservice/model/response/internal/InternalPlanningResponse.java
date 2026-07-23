package org.hvtra.deliveryservice.model.response.internal;

import org.hvtra.deliveryservice.model.enums.DispatchWave;
import org.hvtra.deliveryservice.model.enums.RoutePlanningAlgorithmType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InternalPlanningResponse(
        UUID tenantId,
        DispatchWave dispatchWave,
        RoutePlanningAlgorithmType algorithm,
        Instant plannedStartAt,
        int candidateCount,
        List<InternalRouteResponse> routes,
        List<InternalPlanningRejection> rejected
) {
    public InternalPlanningResponse {
        routes = List.copyOf(routes);
        rejected = List.copyOf(rejected);
    }
}
