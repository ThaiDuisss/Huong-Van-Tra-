package org.hvtra.deliveryservice.carrier.support;

import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ShipmentStatusTransitionPolicy {

    private static final Set<ShipmentStatus> TERMINAL_STATUSES = Set.of(
            ShipmentStatus.DELIVERED,
            ShipmentStatus.RETURNED,
            ShipmentStatus.CANCELLED
    );

    public boolean canApply(ShipmentStatus current, ShipmentStatus target) {
        if (target == null || current == target) {
            return false;
        }
        if (current == null) {
            return true;
        }
        if (TERMINAL_STATUSES.contains(current)) {
            return false;
        }
        if (target == ShipmentStatus.CANCELLED) {
            return true;
        }
        return switch (current) {
            case IN_TRANSIT -> target != ShipmentStatus.READY_FOR_PICKUP && target != ShipmentStatus.PICKED_UP;
            case PICKED_UP -> target != ShipmentStatus.READY_FOR_PICKUP;
            case DELIVERY_FAILED -> target == ShipmentStatus.IN_TRANSIT
                    || target == ShipmentStatus.DELIVERED
                    || target == ShipmentStatus.RETURN_REQUESTED
                    || target == ShipmentStatus.RETURNING
                    || target == ShipmentStatus.RETURNED;
            case RETURN_REQUESTED -> target == ShipmentStatus.RETURNING || target == ShipmentStatus.RETURNED;
            case RETURNING -> target == ShipmentStatus.RETURNED;
            default -> true;
        };
    }
}
