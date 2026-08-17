package org.hvtra.deliveryservice.service.internalDelivery.record;

import org.hvtra.deliveryservice.model.entity.Shipment;

import java.util.Objects;

public record RoutePlanningCandidate(
        Shipment shipment,
        GeoPoint pickup,
        GeoPoint destination
) {
    public RoutePlanningCandidate {
        Objects.requireNonNull(shipment, "shipment is required");
        Objects.requireNonNull(pickup, "pickup is required");
        Objects.requireNonNull(destination, "destination is required");
    }
}
