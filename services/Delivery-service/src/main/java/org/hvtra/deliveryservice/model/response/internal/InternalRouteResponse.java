package org.hvtra.deliveryservice.model.response.internal;

import org.hvtra.deliveryservice.model.enums.DeliveryRouteStatus;
import org.hvtra.deliveryservice.model.enums.DispatchWave;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InternalRouteResponse(
        UUID id,
        UUID tenantId,
        String routeCode,
        DispatchWave dispatchWave,
        DeliveryRouteStatus status,
        UUID courierEmployeeId,
        String courierName,
        Instant plannedStartAt,
        Instant plannedEndAt,
        Instant actualStartAt,
        Instant actualEndAt,
        int totalStops,
        int totalDistanceMeter,
        int totalDurationSecond,
        int totalWeightGram,
        long totalCodAmount,
        String planningAlgorithm,
        List<InternalRouteStopResponse> stops
) {
    public InternalRouteResponse {
        stops = List.copyOf(stops);
    }
}
