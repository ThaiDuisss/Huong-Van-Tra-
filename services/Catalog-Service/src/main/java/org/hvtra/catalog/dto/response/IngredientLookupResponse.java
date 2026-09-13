package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.util.UUID;

public record IngredientLookupResponse(
        UUID id,
        String ingredientCode,
        String name,
        String imageUrl,
        String unitName,
        String unitSymbol,
        Status status
) {
}
