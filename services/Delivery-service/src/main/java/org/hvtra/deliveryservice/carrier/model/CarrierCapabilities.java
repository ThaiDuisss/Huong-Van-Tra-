package org.hvtra.deliveryservice.carrier.model;

import org.hvtra.deliveryservice.model.enums.DeliveryProvider;

public record CarrierCapabilities(
        DeliveryProvider provider,
        boolean multiServiceQuote,
        boolean scheduledOrder,
        boolean recipientPaysShipping,
        boolean balancePayment,
        boolean inspectionPolicy,
        boolean specialRequests,
        boolean routeOptimization,
        boolean proofOfDeliveryFlag,
        boolean receiverVerificationFlag,
        boolean pickupShifts,
        boolean serviceCatalog,
        boolean trackingLink
) {
}
