package org.hvtra.customer.dto.response;

import org.hvtra.customer.model.enums.CustomerPurchaseTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerPurchaseTransactionResponse(
        UUID id,
        UUID customerId,
        UUID orderId,
        UUID referenceId,
        CustomerPurchaseTransactionType transactionType,
        BigDecimal amount,
        Integer orderCountDelta,
        LocalDateTime occurredAt,
        String reason,
        String metadata
) {
}
