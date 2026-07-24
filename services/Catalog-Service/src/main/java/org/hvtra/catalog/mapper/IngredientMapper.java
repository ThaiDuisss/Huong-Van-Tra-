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
                ingredient.getStatus()
        );
    }

    public void update(Ingredient ingredient, IngredientRequest request) {
        ingredient.setName(request.name().trim());
        ingredient.setStatus(request.status());
    }
}
