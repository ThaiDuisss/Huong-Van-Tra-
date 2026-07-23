package org.hvtra.deliveryservice.service.ahmove;

import org.hvtra.deliveryservice.carrier.support.ShipmentStatusTransitionPolicy;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;

import org.hvtra.deliveryservice.model.response.ahmove.AhamoveOrderDetailResponse;
import org.hvtra.deliveryservice.model.response.ahmove.AhamovePathResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class AhamoveStatusMapper {

    private final ShipmentStatusTransitionPolicy transitionPolicy;

    public AhamoveStatusMapper(ShipmentStatusTransitionPolicy transitionPolicy) {
        this.transitionPolicy = transitionPolicy;
    }

    public ShipmentStatus toShipmentStatus(AhamoveOrderDetailResponse detail) {
        if (detail == null) {
            return null;
        }
        return toShipmentStatus(detail.status(), detail.subStatus(), detail.path());
    }

    public ShipmentStatus toShipmentStatus(String status, String subStatus, List<AhamovePathResponse> path) {
        String normalizedStatus = normalize(status);
        String normalizedSubStatus = normalize(subStatus);
        if ("RETURNED".equals(normalizedSubStatus)) {
            return ShipmentStatus.RETURNED;
        }
        if ("IN RETURN".equals(normalizedSubStatus)) {
            return ShipmentStatus.RETURNING;
        }
        return switch (normalizedStatus) {
            case "IDLE", "ASSIGNING", "ACCEPTED", "CONFIRMING", "PAYING" -> ShipmentStatus.READY_FOR_PICKUP;
            case "IN PROCESS" -> ShipmentStatus.IN_TRANSIT;
            case "COMPLETED" -> completedStatus(path, normalizedSubStatus);
            case "CANCELLED" -> ShipmentStatus.CANCELLED;
            default -> null;
        };
    }

    public boolean canApply(ShipmentStatus current, ShipmentStatus target) {
        return transitionPolicy.canApply(current, target);
    }

    private ShipmentStatus completedStatus(List<AhamovePathResponse> path, String subStatus) {
        List<AhamovePathResponse> deliveries = path == null || path.size() < 2
                ? List.of()
                : path.subList(1, path.size());
        if (!deliveries.isEmpty() && deliveries.stream().allMatch(point -> "COMPLETED".equals(normalize(point.status())))) {
            return ShipmentStatus.DELIVERED;
        }
        if (deliveries.stream().anyMatch(point -> "FAILED".equals(normalize(point.status())))) {
            if ("IN RETURN".equals(subStatus)) {
                return ShipmentStatus.RETURNING;
            }
            if ("RETURNED".equals(subStatus)) {
                return ShipmentStatus.RETURNED;
            }
            return ShipmentStatus.DELIVERY_FAILED;
        }
        return ShipmentStatus.IN_TRANSIT;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replace('_', ' ').toUpperCase(Locale.ROOT);
    }
}
