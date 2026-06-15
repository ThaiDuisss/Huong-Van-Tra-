package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.Status;

public record IngredientResponse(
        Long id,
        String ingredientCode,
        String name,
        UnitResponse baseUnit,
        Status status
) {
}
