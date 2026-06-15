package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;

import java.util.UUID;

public record LoyaltyTransactionResponse(
        UUID id,
        UUID loyaltyAccountId,
        UUID customerId,
        LoyaltyTransactionType type,
        Integer points,
        LoyaltyReferenceType referenceType,
        UUID referenceId
) {
}
