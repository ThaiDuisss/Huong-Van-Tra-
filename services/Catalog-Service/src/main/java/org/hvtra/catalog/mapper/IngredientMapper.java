package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.entity.Ingredient;
import org.springframework.stereotype.Component;

@Component
public class IngredientMapper {

    public IngredientResponse toResponse(Ingredient ingredient) {
        return new IngredientResponse(
                ingredient.getId(),
                ingredient.getIngredientCode(),
                ingredient.getName(),
                ingredient.getUnit() != null ? ingredient.getUnit().getId() : null,
                ingredient.getUnit() != null ? ingredient.getUnit().getName() : null,
                ingredient.getUnit() != null ? ingredient.getUnit().getSymbol() : null,
                ingredient.getReorderPoint(),
                ingredient.getStatus()
        );
    }

    public void update(Ingredient ingredient, IngredientRequest request) {
        ingredient.setName(request.name().trim());
        if (request.reorderPoint() != null) {
            ingredient.setReorderPoint(request.reorderPoint());
        }
        ingredient.setStatus(request.status());
    }
}
