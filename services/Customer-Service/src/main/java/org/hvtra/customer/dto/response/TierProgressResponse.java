package org.hvtra.customer.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TierProgressResponse(
        CustomerTierResponse currentTier,
        CustomerTierResponse qualifiedTierForCurrentYear,
        Integer currentYear,
        BigDecimal totalSpent,
        Integer totalOrderCount,
        CustomerTierResponse nextTier,
        BigDecimal remainingSpending,
        Integer remainingOrderCount,
        LocalDateTime currentTierEffectiveUntil
) {
}
