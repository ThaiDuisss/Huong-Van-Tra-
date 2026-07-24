package org.hvtra.deliveryservice.carrier.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.List;

// dữ liệu đầu vào chung để yêu cầu các hãng vận chuyển báo giá
public record CarrierQuoteCommand(
        List<String> serviceCodes,
        @Positive Integer serviceTypeId,
        @Valid List<CarrierSpecialRequest> specialRequests,
        CarrierPaymentMethod paymentMethod,
        Instant scheduledAt,
        String promoCode,
        Boolean routeOptimized,
        Integer codFailedAmount,
        String note,
        String pickupNote,
        String deliveryNote
) {
    @AssertTrue(message = "serviceCodes or serviceTypeId must be provided")
    public boolean hasServiceSelection() {
        return (serviceCodes != null && serviceCodes.stream().anyMatch(code -> code != null && !code.isBlank()))
                || (serviceTypeId != null && serviceTypeId > 0);
    }

    public CarrierPaymentMethod resolvedPaymentMethod() {
        return paymentMethod == null ? CarrierPaymentMethod.SENDER_CASH : paymentMethod;
    }

    public List<CarrierSpecialRequest> resolvedSpecialRequests() {
        return specialRequests == null ? List.of() : List.copyOf(specialRequests);
    }
}
