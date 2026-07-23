package org.hvtra.deliveryservice.model.request.internal;

import jakarta.validation.constraints.*;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.RoutePlanningAlgorithmType;
import org.hvtra.deliveryservice.model.enums.TravelEstimationMode;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ConfigureTenantDeliveryRequest(
        @NotNull Boolean internalDeliveryEnabled,
        @DecimalMin("0.01") BigDecimal maxDeliveryDistanceKm,
        @NotNull @Min(1) @Max(500) Integer maxOrdersPerRoute,
        @Positive Integer maxRouteWeightGram,
        @PositiveOrZero Long maxRouteCodAmount,
        @Positive Integer maxRouteDurationMin,
        @NotNull @PositiveOrZero Long baseShippingFee,
        @NotNull @PositiveOrZero Long feePerKm,
        DeliveryProvider fallbackProvider,
        LocalTime morningCutoffTime,
        LocalTime afternoonCutoffTime,
        LocalTime eveningCutoffTime,
        @NotNull RoutePlanningAlgorithmType planningAlgorithm,
        @NotNull @DecimalMin("1.00") @DecimalMax("100.00") BigDecimal averageSpeedKmh,
        TravelEstimationMode travelEstimationMode,
        @NotNull @Min(0) @Max(180) Integer serviceTimePerStopMin,
        Boolean active
) {
}
