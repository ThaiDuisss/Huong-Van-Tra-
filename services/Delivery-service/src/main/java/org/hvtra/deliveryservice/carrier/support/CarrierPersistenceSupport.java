package org.hvtra.deliveryservice.carrier.support;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.deliveryservice.carrier.model.CarrierShipmentResult;
import org.hvtra.deliveryservice.model.entity.CarrierShipment;
import org.hvtra.deliveryservice.model.entity.Shipment;
import org.hvtra.deliveryservice.model.entity.ShipmentStatusHistory;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;
import org.hvtra.deliveryservice.model.enums.ShipmentStatusSource;
import org.hvtra.deliveryservice.repository.CarrierShipmentRepository;
import org.hvtra.deliveryservice.repository.ShipmentRepository;
import org.hvtra.deliveryservice.repository.ShipmentStatusHistoryRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CarrierPersistenceSupport {

    private final ShipmentRepository shipmentRepository;
    private final CarrierShipmentRepository carrierShipmentRepository;
    private final ShipmentStatusHistoryRepository historyRepository;
    private final ShipmentStatusTransitionPolicy transitionPolicy;


    public Shipment getShipment(UUID shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
    }

    public Shipment getShipmentForUpdate(UUID shipmentId) {
        return shipmentRepository.findByIdForUpdate(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
    }

    public CarrierShipment getCarrier(UUID shipmentId, DeliveryProvider provider) {
        return carrierShipmentRepository.findByShipmentIdAndProvider(shipmentId, provider)
                .orElseThrow(() -> new ResourceNotFoundException(provider + " carrier shipment not found"));
    }

    public boolean applyStatus(
            Shipment shipment,
            CarrierShipment carrier,
            String providerStatus,
            ShipmentStatus target,
            Instant occurredAt,
            String reasonCode,
            String reason,
            ShipmentStatusSource source,
            String sourceReference,
            JsonNode metadata
    ) {
        carrier.updateProviderStatus(providerStatus);
        carrier.markSynced(Instant.now());
        if (!transitionPolicy.canApply(shipment.getStatus(), target)) {
            return false;
        }
        ShipmentStatus previous = shipment.getStatus();
        boolean changed = shipment.synchronizeCarrierStatus(
                target,
                occurredAt == null ? Instant.now() : occurredAt,
                reasonCode,
                reason
        );
        if (target == ShipmentStatus.DELIVERED && !carrier.isCodCollected()) {
            carrier.markCodCollected();
        }
        if (changed) {
            historyRepository.save(ShipmentStatusHistory.record(
                    shipment,
                    previous,
                    shipment.getStatus(),
                    source,
                    sourceReference,
                    reasonCode,
                    reason,
                    "delivery-service",
                    occurredAt == null ? Instant.now() : occurredAt,
                    metadata
            ));
        }
        return changed;
    }

    public CarrierShipmentResult toResult(Shipment shipment, CarrierShipment carrier, JsonNode detail) {
        return new CarrierShipmentResult(
                shipment.getId(),
                shipment.getShipmentCode(),
                carrier.getProvider(),
                carrier.getClientOrderCode(),
                carrier.getExternalOrderCode(),
                carrier.getTrackingCode(),
                carrier.getTrackingUrl(),
                carrier.getProviderStatus(),
                shipment.getStatus(),
                carrier.getServiceCode(),
                carrier.getTotalFee(),
                carrier.getExpectedDeliveryTo(),
                carrier.getLastSyncedAt(),
                detail
        );
    }
}
