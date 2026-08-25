package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.util.UUID;

public record IngredientResponse(
        UUID id,
        String ingredientCode,
        String name,
        UUID unitId,
        String unitName,
        String unitSymbol,
        Integer reorderPoint,
        Status status
) {
}
