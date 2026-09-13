package org.hvtra.iventoryservice.counting.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record AdjustmentLineResponse(
        UUID id,
        Integer lineNo,
        UUID inventoryItemId,
        UUID locationId,
        BigDecimal adjustmentQuantity,
        BigDecimal appliedQuantity,
        String note
) {}
