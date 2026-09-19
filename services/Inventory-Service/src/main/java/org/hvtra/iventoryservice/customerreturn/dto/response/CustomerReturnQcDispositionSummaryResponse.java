package org.hvtra.iventoryservice.customerreturn.dto.response;

import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;
import org.hvtra.iventoryservice.domain.enums.InventoryTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerReturnQcDispositionSummaryResponse(
        CustomerReturnQcResult result,
        BigDecimal quantity,
        UUID destinationLocationId,
        String destinationLocationName,
        UUID transactionId,
        InventoryTransactionType transactionType,
        String processingStatus,
        Instant processedAt
) {
    public CustomerReturnQcDispositionSummaryResponse(
            CustomerReturnQcResult result,
            BigDecimal quantity
    ) {
        this(result, quantity, null, null, null, null, null, null);
    }
}
