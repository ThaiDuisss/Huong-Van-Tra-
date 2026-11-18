package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.LoyaltyReferenceType;
import org.hvtra.customer.model.enums.LoyaltyTransactionType;

import java.time.Instant;
import java.util.UUID;

public record LoyaltyTransactionResponse(
        UUID id,
        UUID customerId,
        LoyaltyTransactionType type,
        Integer points,
        Integer balanceAfter,
        LoyaltyReferenceType referenceType,
        /* Mã hiển thị của chứng từ (mã đơn / mã phiếu hoàn), không phải UUID. */
        String referenceCode,
        String reason,
        Instant createdAt
) {
}
