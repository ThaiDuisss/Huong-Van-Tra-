package org.hvtra.iventoryservice.customerreturn.dto.response;

import org.hvtra.iventoryservice.domain.enums.CustomerReturnQcResult;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerReturnInspectionLineResponse(
        UUID id,
        UUID returnItemId,
        UUID orderItemId,
        UUID productVariantId,
        UUID inventoryItemId,
        BigDecimal quantity,
        CustomerReturnQcResult result,
        UUID destinationLocationId,
        String note,
        Boolean posted,
        UUID transactionId
) {
}
