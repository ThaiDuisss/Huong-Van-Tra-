package org.hvtra.deliveryservice.service.internalDelivery.routing;


import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;

import java.time.Instant;
import java.util.Optional;

public interface RoadTravelEstimator {
    Optional<TravelEstimate> estimate(GeoPoint from, GeoPoint to, Instant departureAt);
}
