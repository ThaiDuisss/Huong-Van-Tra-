package org.hvtra.deliveryservice.model.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record GhnCreateShipmentCommand(
        Integer serviceId,
        Integer serviceTypeId,
        @Min(1) @Max(2) Integer paymentTypeId,
        String requiredNote,
        String note,
        String coupon,
        List<Integer> pickShift
) {
    @AssertTrue(message = "a positive serviceId or serviceTypeId must be provided")
    public boolean hasService() {
        return isPositive(serviceId) || isPositive(serviceTypeId);
    }

    @AssertTrue(message = "pickShift values must be positive")
    public boolean hasValidPickShift() {
        return pickShift == null || pickShift.stream().allMatch(this::isPositive);
    }

    private boolean isPositive(Integer value) {
        return value != null && value > 0;
    }
}
