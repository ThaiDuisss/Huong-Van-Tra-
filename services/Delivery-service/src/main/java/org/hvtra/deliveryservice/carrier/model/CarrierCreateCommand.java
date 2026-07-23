package org.hvtra.deliveryservice.carrier.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

public record CarrierCreateCommand(
        String serviceCode,
        @Positive Integer serviceTypeId,
        @Valid List<CarrierSpecialRequest> specialRequests,
        CarrierPaymentMethod paymentMethod,
        CarrierInspectionPolicy inspectionPolicy,
        Instant scheduledAt,
        String promoCode,
        String note,
        String pickupNote,
        String deliveryNote,
        Boolean routeOptimized,
        Boolean requireProofOfDelivery,
        Boolean requireReceiverVerification,
        List<@Positive Integer> pickupShifts
) {
    @AssertTrue(message = "serviceCode or serviceTypeId is required")
    public boolean hasServiceSelection() {
        return (serviceCode != null && !serviceCode.isBlank()) || serviceTypeId != null;
    }

    public CarrierPaymentMethod resolvedPaymentMethod() {
        return paymentMethod == null ? CarrierPaymentMethod.SENDER_CASH : paymentMethod;
    }

    public CarrierInspectionPolicy resolvedInspectionPolicy() {
        return inspectionPolicy == null ? CarrierInspectionPolicy.NO_INSPECTION : inspectionPolicy;
    }

    public List<CarrierSpecialRequest> resolvedSpecialRequests() {
        return specialRequests == null ? List.of() : List.copyOf(specialRequests);
    }
}
