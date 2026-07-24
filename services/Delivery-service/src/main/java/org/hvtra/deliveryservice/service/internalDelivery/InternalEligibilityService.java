package org.hvtra.deliveryservice.service.internalDelivery;

import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.exception.ResourceNotFoundException;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.entity.TenantDeliveryConfig;
import org.hvtra.deliveryservice.model.enums.DeliveryMode;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.model.enums.ShipmentStatusSource;
import org.hvtra.deliveryservice.model.response.internal.InternalEligibilityResponse;
import org.hvtra.deliveryservice.repository.ShipmentRepository;
import org.hvtra.deliveryservice.repository.TenantDeliveryConfigRepository;
import org.hvtra.deliveryservice.service.internalDelivery.helper.InternalShipmentTransitionService;
import org.hvtra.deliveryservice.service.internalDelivery.record.GeoPoint;
import org.hvtra.deliveryservice.service.internalDelivery.record.RoutePlanningCandidate;
import org.hvtra.deliveryservice.service.internalDelivery.routing.RouteTravelEstimator;
import org.hvtra.deliveryservice.service.internalDelivery.routing.TravelEstimate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalEligibilityService {

    private final ShipmentRepository shipmentRepository;
    private final TenantDeliveryConfigRepository configRepository;
    private final RouteTravelEstimator travelEstimator;
    private final InternalShipmentTransitionService transitionService;


    @Transactional(readOnly = true)
    public InternalEligibilityResponse evaluate(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
        return evaluate(shipment, requireConfig(shipment.getTenantId()));
    }

    @Transactional
    public InternalEligibilityResponse markReadyForPlanning(UUID shipmentId) {
        Shipment shipment = shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
        TenantDeliveryConfig config = requireConfig(shipment.getTenantId());
        InternalEligibilityResponse eligibility = evaluate(shipment, config);
        if (!eligibility.eligible()) {
            throw new IllegalStateException("Shipment is not eligible for internal delivery: "
                    + String.join(", ", eligibility.reasons()));
        }
        if (shipment.getStatus() == ShipmentStatus.READY_FOR_PLANNING) {
            return eligibility;
        }
        if (shipment.getStatus() != ShipmentStatus.PENDING) {
            throw new IllegalStateException("Only pending shipment can enter internal planning; use retry for failed shipment");
        }
        if (eligibility.estimatedShippingFee() != null) {
            shipment.applyInternalShippingFee(eligibility.estimatedShippingFee());
        }
        transitionService.transition(
                shipment,
                shipment::markReadyForPlanning,
                ShipmentStatusSource.SYSTEM,
                "internal-eligibility",
                null,
                null,
                "delivery-service"
        );
        return eligibility;
    }

    InternalEligibilityResponse evaluate(Shipment shipment, TenantDeliveryConfig config) {
        List<String> reasons = new ArrayList<>();
        if (!config.isActive()) {
            reasons.add("TENANT_CONFIG_INACTIVE");
        }
        if (!config.isInternalDeliveryEnabled()) {
            reasons.add("INTERNAL_DELIVERY_DISABLED");
        }
        if (shipment.getDeliveryMode() != DeliveryMode.INTERNAL) {
            reasons.add("DELIVERY_MODE_NOT_INTERNAL");
        }
        if (shipment.getProvider() != DeliveryProvider.STORE_COURIER) {
            reasons.add("PROVIDER_NOT_STORE_COURIER");
        }
        if (shipment.getStatus() != ShipmentStatus.PENDING
                && shipment.getStatus() != ShipmentStatus.READY_FOR_PLANNING
                && shipment.getStatus() != ShipmentStatus.DELIVERY_FAILED) {
            reasons.add("INVALID_SHIPMENT_STATUS");
        }
        if (shipment.getTotalWeightGram() < 0 || shipment.getCodAmount() < 0) {
            reasons.add("INVALID_WEIGHT_OR_COD");
        }

        BigDecimal distanceKm = null;
        TravelEstimate travelEstimate = null;
        try {
            RoutePlanningCandidate candidate = toPlanningCandidate(shipment);
            Instant departureAt = shipment.getRequestedFrom() == null
                    ? Instant.now()
                    : shipment.getRequestedFrom();
            travelEstimate = travelEstimator.estimate(
                    candidate.pickup(),
                    candidate.destination(),
                    departureAt,
                    config.getAverageSpeedKmh(),
                    config.getTravelEstimationMode()
            );
            distanceKm = travelEstimate.distanceKm();
        } catch (IllegalArgumentException exception) {
            reasons.add("MISSING_OR_INVALID_COORDINATES");
        }
        if (config.getMaxDeliveryDistanceKm() != null
                && distanceKm != null
                && distanceKm.compareTo(config.getMaxDeliveryDistanceKm()) > 0) {
            reasons.add("MAX_DELIVERY_DISTANCE_EXCEEDED");
        }
        if (config.getMaxRouteWeightGram() != null
                && shipment.getTotalWeightGram() > config.getMaxRouteWeightGram()) {
            reasons.add("MAX_ROUTE_WEIGHT_EXCEEDED");
        }
        if (config.getMaxRouteCodAmount() != null
                && shipment.getCodAmount() > config.getMaxRouteCodAmount()) {
            reasons.add("MAX_ROUTE_COD_EXCEEDED");
        }
        Long estimatedFee = distanceKm == null ? null : config.calculateInternalShippingFee(distanceKm);
        return new InternalEligibilityResponse(
                shipment.getId(),
                shipment.getShipmentCode(),
                reasons.isEmpty(),
                reasons,
                distanceKm,
                travelEstimate == null ? null : travelEstimate.durationSecond(),
                travelEstimate == null ? null : travelEstimate.source(),
                estimatedFee,
                config.getFallbackProvider()
        );
    }


    RoutePlanningCandidate toPlanningCandidate(Shipment shipment) {
        if (shipment.getPickupLatitude() == null
                || shipment.getPickupLongitude() == null
                || shipment.getDeliveryLatitude() == null
                || shipment.getDeliveryLongitude() == null) {
            throw new IllegalArgumentException("Pickup and delivery coordinates are required");
        }
        return new RoutePlanningCandidate(
                shipment,
                new GeoPoint(shipment.getPickupLatitude(), shipment.getPickupLongitude()),
                new GeoPoint(shipment.getDeliveryLatitude(), shipment.getDeliveryLongitude())
        );
    }

    private TenantDeliveryConfig requireConfig(UUID tenantId) {
        return configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tenant delivery config not found: " + tenantId
                ));
    }
}
