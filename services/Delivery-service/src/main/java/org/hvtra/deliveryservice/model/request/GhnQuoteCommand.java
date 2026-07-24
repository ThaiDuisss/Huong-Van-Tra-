package org.hvtra.deliveryservice.model.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

public record GhnQuoteCommand(
        Integer serviceId,
        Integer serviceTypeId,
        @Min(0) Integer codFailedAmount,
        String coupon
) {
    @AssertTrue(message = "a positive serviceId or serviceTypeId must be provided")
    public boolean hasService() {
        return isPositive(serviceId) || isPositive(serviceTypeId);
    }

    private boolean isPositive(Integer value) {
        return value != null && value > 0;
    }
}
