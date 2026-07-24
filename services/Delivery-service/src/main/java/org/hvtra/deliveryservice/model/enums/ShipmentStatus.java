package org.hvtra.deliveryservice.model.enums;

public enum ShipmentStatus {
    PENDING,
    READY_FOR_PLANNING,
    PLANNED,
    ASSIGNED,
    READY_FOR_PICKUP,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    DELIVERY_FAILED,
    RETURN_REQUESTED,
    RETURNING,
    RETURNED,
    CANCELLED
}
