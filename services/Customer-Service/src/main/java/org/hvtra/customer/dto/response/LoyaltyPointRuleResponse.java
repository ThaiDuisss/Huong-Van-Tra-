package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.LoyaltyPointRuleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoyaltyPointRuleResponse(
        UUID id,
        String code,
        String name,
        String description,
        UUID tierId,
        String tierCode,
        String tierName,
        BigDecimal spendAmountPerBlock,
        Integer pointsPerBlock,
        BigDecimal minOrderAmount,
        Boolean earnAfterDiscount,
        Boolean allowCouponUsage,
        BigDecimal pointMultiplier,
        Integer priority,
        Instant effectiveFrom,
        Instant effectiveTo,
        LoyaltyPointRuleStatus status
) {
}
