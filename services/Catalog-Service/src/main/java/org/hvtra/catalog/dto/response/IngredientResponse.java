package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

public record IngredientResponse(
        Long id,
        String ingredientCode,
        String name,
        UnitResponse baseUnit,
        Status status
) {
}
