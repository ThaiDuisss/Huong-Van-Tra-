package org.hvtra.deliveryservice.model.response.internal;

import org.hvtra.deliveryservice.model.enums.RouteStopStatus;
import org.hvtra.deliveryservice.model.enums.TravelEstimateSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InternalRouteStopResponse(
        UUID id,
        UUID shipmentId,
        String shipmentCode,
        int sequenceNo,
        RouteStopStatus status,
        String recipientName,
        String recipientPhone,
        String deliveryAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant plannedArrivalAt,
        Instant actualArrivalAt,
        Instant completedAt,
        Integer estimatedDistanceMeter,
        Integer estimatedDurationSecond,
        TravelEstimateSource estimateSource,
        String failureCode,
        String failureReason
) {
}
