package org.hvtra.customer.dto.response;

import java.util.UUID;

public record LoyaltyAccountResponse(
        UUID id,
        UUID customerId,
        String customerCode,
        String customerName,
        Integer pointBalance
) {
}
