package org.hvtra.iventoryservice.disposal.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record DisposalRequestLineResponse(
        UUID id,
        UUID inspectionLineId,
        UUID inventoryItemId,
        String itemCode,
        String itemName,
        BigDecimal quantity,
        UUID transactionId
) {
}
