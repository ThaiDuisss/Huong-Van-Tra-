package org.hvtra.deliveryservice.model.response.internal;

import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.RoutePlanningAlgorithmType;
import org.hvtra.deliveryservice.model.enums.TravelEstimationMode;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

public record TenantDeliveryConfigResponse(
        UUID id,
        UUID tenantId,
        boolean internalDeliveryEnabled,
        BigDecimal maxDeliveryDistanceKm,
        int maxOrdersPerRoute,
        Integer maxRouteWeightGram,
        Long maxRouteCodAmount,
        Integer maxRouteDurationMin,
        long baseShippingFee,
        long feePerKm,
        DeliveryProvider fallbackProvider,
        LocalTime morningCutoffTime,
        LocalTime afternoonCutoffTime,
        LocalTime eveningCutoffTime,
        RoutePlanningAlgorithmType planningAlgorithm,
        BigDecimal averageSpeedKmh,
        TravelEstimationMode travelEstimationMode,
        int serviceTimePerStopMin,
        boolean active
) {
}
