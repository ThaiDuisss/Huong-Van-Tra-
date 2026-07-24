package org.hvtra.deliveryservice.model.response.ghn;

import org.hvtra.deliveryservice.model.enums.ShipmentStatus;

import java.time.Instant;
import java.util.UUID;

public record GhnShipmentResponse(
        UUID shipmentId,
        String shipmentCode,
        String clientOrderCode,
        String orderCode,
        String trackingCode,
        String providerStatus,
        ShipmentStatus shipmentStatus,
        String serviceCode,
        Long totalFee,
        Instant expectedDeliveryAt
) {
}
