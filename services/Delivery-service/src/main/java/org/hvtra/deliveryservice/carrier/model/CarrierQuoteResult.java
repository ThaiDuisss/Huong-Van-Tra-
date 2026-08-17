package org.hvtra.deliveryservice.carrier.model;

import com.fasterxml.jackson.databind.JsonNode;
import org.hvtra.deliveryservice.model.enums.DeliveryProvider;

import java.util.UUID;

public record CarrierQuoteResult(
        UUID shipmentId,
        DeliveryProvider provider,
        String serviceCode,
        Long totalFee,
        Long shippingFee,
        Long insuranceFee,
        Long codFee,
        Double distanceKm,
        Long durationSeconds,
        JsonNode detail
) {
}
