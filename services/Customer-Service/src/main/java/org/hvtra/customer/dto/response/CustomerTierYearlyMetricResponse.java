package org.hvtra.customer.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerTierYearlyMetricResponse(
        UUID id,
        UUID customerId,
        Integer tierYear,
        BigDecimal totalSpent,
        Integer totalOrderCount,
        CustomerTierResponse qualifiedTier,
        LocalDateTime calculatedAt
) {
}
