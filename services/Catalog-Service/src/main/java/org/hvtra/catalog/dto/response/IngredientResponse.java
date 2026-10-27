package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.util.UUID;

public record IngredientResponse(
        UUID id,
        String ingredientCode,
        String name,
        String imageUrl,
        UUID unitId,
        String unitName,
        String unitSymbol,
        Integer reorderPoint,
        Integer type,
        Status status,
        BigDecimal retailPricePerUnit
) {
}
