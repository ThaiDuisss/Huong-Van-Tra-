package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.Status;

import java.util.UUID;

public record IngredientLookupResponse(
        UUID id,
        String ingredientCode,
        String name,
        Status status
) {
}
