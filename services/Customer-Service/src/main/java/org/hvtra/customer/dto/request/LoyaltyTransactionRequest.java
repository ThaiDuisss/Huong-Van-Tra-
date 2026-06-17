package org.hvtra.customer.dto.request;

import jakarta.validation.constraints.NotNull;
import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;

import java.util.UUID;

public record LoyaltyTransactionRequest(
        @NotNull UUID loyaltyAccountId,
        @NotNull LoyaltyTransactionType type,
        @NotNull Integer points,
        LoyaltyReferenceType referenceType,
        UUID referenceId
) {
}
