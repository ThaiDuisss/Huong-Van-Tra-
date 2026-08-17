package org.hvtra.deliveryservice.service.internalDelivery.routing;

import org.hvtra.deliveryservice.model.enums.TravelEstimateSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record TravelEstimate(
        int distanceMeter,
        int durationSecond,
        TravelEstimateSource source
) {
    public TravelEstimate {
        if (distanceMeter < 0 || durationSecond < 0) {
            throw new IllegalArgumentException("Travel estimate values must not be negative");
        }
        Objects.requireNonNull(source, "source is required");
    }

    public BigDecimal distanceKm() {
        return BigDecimal.valueOf(distanceMeter)
                .divide(BigDecimal.valueOf(1_000), 3, RoundingMode.HALF_UP);
    }
}
