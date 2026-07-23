package org.hvtra.deliveryservice.carrier.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;
import org.hvtra.deliveryservice.model.enums.ShipmentStatus;

import java.time.Instant;
import java.util.UUID;

public record CarrierShipmentResult(
        UUID shipmentId,
        String shipmentCode,
        DeliveryProvider provider,
        String clientOrderCode,
        String externalOrderCode,
        String trackingCode,
        String trackingUrl,
        String providerStatus,
        ShipmentStatus shipmentStatus,
        String serviceCode,
        Long totalFee,
        Instant expectedDeliveryAt,
        Instant lastSyncedAt,
        JsonNode detail
) {
}
