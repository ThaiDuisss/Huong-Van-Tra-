package org.hvtra.deliveryservice.service.internalDelivery.routing;

import org.hvtra.deliveryservice.model.enums.TravelEstimationMode;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;

import java.math.BigDecimal;
import java.time.Instant;

public interface RouteTravelEstimator {
    TravelEstimate estimate(
            GeoPoint from,
            GeoPoint to,
            Instant departureAt,
            BigDecimal fallbackAverageSpeedKmh,
            TravelEstimationMode mode
    );
}
