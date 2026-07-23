package org.hvtra.deliveryservice.service.ghn;

import org.hvtra.deliveryservice.carrier.support.ShipmentStatusTransitionPolicy;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class GhnStatusMapper {

    private final ShipmentStatusTransitionPolicy transitionPolicy;

    public GhnStatusMapper(ShipmentStatusTransitionPolicy transitionPolicy) {
        this.transitionPolicy = transitionPolicy;
    }

    public ShipmentStatus toShipmentStatus(String ghnStatus) {
        if (ghnStatus == null || ghnStatus.isBlank()) {
            return null;
        }
        return switch (ghnStatus.toLowerCase(Locale.ROOT)) {
            case "ready_to_pick", "picking", "money_collect_picking" -> ShipmentStatus.READY_FOR_PICKUP;
            case "picked" -> ShipmentStatus.PICKED_UP;
            case "storing", "transporting", "sorting", "delivering", "money_collect_delivering" -> ShipmentStatus.IN_TRANSIT;
            case "delivered" -> ShipmentStatus.DELIVERED;
            case "delivery_fail", "exception", "damage", "lost", "return_fail" -> ShipmentStatus.DELIVERY_FAILED;
            case "waiting_to_return", "return" -> ShipmentStatus.RETURN_REQUESTED;
            case "return_transporting", "return_sorting", "returning" -> ShipmentStatus.RETURNING;
            case "returned" -> ShipmentStatus.RETURNED;
            case "cancel" -> ShipmentStatus.CANCELLED;
            default -> null;
        };
    }

    public boolean canApply(ShipmentStatus current, ShipmentStatus target) {
        return transitionPolicy.canApply(current, target);
    }
}
