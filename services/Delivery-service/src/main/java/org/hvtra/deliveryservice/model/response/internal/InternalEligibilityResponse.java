package org.hvtra.deliveryservice.model.response.internal;

import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.TravelEstimateSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

//response đeể xem đơn đủ điều kiện trong internal không
public record InternalEligibilityResponse(
        UUID shipmentId,
        String shipmentCode,
        boolean eligible,
        List<String> reasons,
        BigDecimal estimatedDistanceKm,
        Integer estimatedTravelDurationSecond,
        TravelEstimateSource estimateSource,
        Long estimatedShippingFee,
        DeliveryProvider fallbackProvider
) {
    public InternalEligibilityResponse {
        reasons = List.copyOf(reasons);
    }
}
