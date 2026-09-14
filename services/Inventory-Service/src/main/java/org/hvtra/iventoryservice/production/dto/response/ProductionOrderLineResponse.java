package org.hvtra.iventoryservice.production.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductionOrderLineResponse(
        UUID id,
        UUID ingredientId,
        String ingredientName,
        BigDecimal requiredQuantity,
        UUID unitId,
        BigDecimal wasteRate,
        BigDecimal reservedQuantity,
        BigDecimal consumedQuantity
) {
}
