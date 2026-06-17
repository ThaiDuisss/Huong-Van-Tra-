package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.CustomerTierStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerTierResponse(
        UUID id,
        String code,
        String name,
        BigDecimal minSpending,
        BigDecimal discountRate,
        CustomerTierStatus status
) {
}
