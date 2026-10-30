package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hvtra.customer.model.enums.LoyaltyPointRuleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoyaltyPointRuleRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        UUID tierId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal spendAmountPerBlock,
        @NotNull @Min(1) Integer pointsPerBlock,
        @DecimalMin("0.00") BigDecimal minOrderAmount,
        Boolean earnAfterDiscount,
        Boolean allowCouponUsage,
        @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 3, fraction = 2) BigDecimal pointMultiplier,
        Integer priority,
        @NotNull Instant effectiveFrom,
        Instant effectiveTo,
        @NotNull LoyaltyPointRuleStatus status
) {
    @AssertTrue(message = "effectiveTo phải sau effectiveFrom")
    public boolean isEffectiveRangeValid() {
        return effectiveFrom == null || effectiveTo == null || effectiveTo.isAfter(effectiveFrom);
    }
}
