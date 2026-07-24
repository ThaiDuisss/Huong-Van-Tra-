package org.hvtra.deliveryservice.service.internalDelivery.helper;

import org.hvtra.deliveryservice.model.entity.DeliveryRoute;
import org.hvtra.deliveryservice.model.entity.RouteStop;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.entity.TenantDeliveryConfig;
import org.hvtra.deliveryservice.model.response.internal.InternalRouteResponse;
import org.hvtra.deliveryservice.model.response.internal.InternalRouteStopResponse;
import org.hvtra.deliveryservice.model.response.internal.TenantDeliveryConfigResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class InternalDeliveryResponseMapper {

    public TenantDeliveryConfigResponse toConfigResponse(TenantDeliveryConfig config) {
        return new TenantDeliveryConfigResponse(
                config.getId(),
                config.getTenantId(),
                config.isInternalDeliveryEnabled(),
                config.getMaxDeliveryDistanceKm(),
                config.getMaxOrdersPerRoute(),
                config.getMaxRouteWeightGram(),
                config.getMaxRouteCodAmount(),
                config.getMaxRouteDurationMin(),
                config.getBaseShippingFee(),
                config.getFeePerKm(),
                config.getFallbackProvider(),
                config.getMorningCutoffTime(),
                config.getAfternoonCutoffTime(),
                config.getEveningCutoffTime(),
                config.getPlanningAlgorithm(),
                config.getAverageSpeedKmh(),
                config.getTravelEstimationMode(),
                config.getServiceTimePerStopMin(),
                config.isActive()
        );
    }

    public InternalRouteResponse toRouteResponse(DeliveryRoute route) {
        return new InternalRouteResponse(
                route.getId(),
                route.getTenantId(),
                route.getRouteCode(),
                route.getDispatchWave(),
                route.getStatus(),
                route.getCourierEmployeeId(),
                route.getCourierNameSnapshot(),
                route.getPlannedStartAt(),
                route.getPlannedEndAt(),
                route.getActualStartAt(),
                route.getActualEndAt(),
                route.getTotalStops(),
                route.getTotalDistanceMeter(),
                route.getTotalDurationSecond(),
                route.getTotalWeightGram(),
                route.getTotalCodAmount(),
                route.getPlanningAlgorithm(),
                route.getStops().stream()
                        .sorted(Comparator.comparingInt(RouteStop::getSequenceNo))
                        .map(this::toStopResponse)
                        .toList()
        );
    }

    private InternalRouteStopResponse toStopResponse(RouteStop stop) {
        Shipment shipment = stop.getShipment();
        return new InternalRouteStopResponse(
                stop.getId(),
                shipment.getId(),
                shipment.getShipmentCode(),
                stop.getSequenceNo(),
                stop.getStatus(),
                shipment.getRecipientName(),
                shipment.getRecipientPhone(),
                shipment.getDeliveryAddress(),
                shipment.getDeliveryLatitude(),
                shipment.getDeliveryLongitude(),
                stop.getPlannedArrivalAt(),
                stop.getActualArrivalAt(),
                stop.getCompletedAt(),
                stop.getEstimatedDistanceMeter(),
                stop.getEstimatedDurationSecond(),
                stop.getEstimateSource(),
                stop.getFailureCode(),
                stop.getFailureReason()
        );
    }
}
