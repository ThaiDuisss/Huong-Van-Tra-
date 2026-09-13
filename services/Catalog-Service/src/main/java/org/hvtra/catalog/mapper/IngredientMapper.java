package org.hvtra.catalog.mapper;

import jakarta.persistence.EntityNotFoundException;
import org.hvtra.catalog.dto.request.IngredientRequest;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.entity.Ingredient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IngredientMapper {

    public IngredientResponse toResponse(Ingredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        UUID unitId = null;
        String unitName = null;
        String unitSymbol = null;
        try {
            if (ingredient.getUnit() != null) {
                unitId = ingredient.getUnit().getId();
                unitName = ingredient.getUnit().getName();
                unitSymbol = ingredient.getUnit().getSymbol();
            }
        } catch (EntityNotFoundException ignored) {
            unitId = null;
            unitName = null;
            unitSymbol = null;
        }

        return new IngredientResponse(
                ingredient.getId(),
                ingredient.getIngredientCode(),
                ingredient.getName(),
                ingredient.getImageUrl(),
                unitId,
                unitName,
                unitSymbol,
                ingredient.getReorderPoint(),
                ingredient.getStatus()
        );
    }

    public void update(Ingredient ingredient, IngredientRequest request) {
        ingredient.setName(request.name().trim());
        ingredient.setImageUrl(request.imageUrl());
        if (request.reorderPoint() != null) {
            ingredient.setReorderPoint(request.reorderPoint());
        }
        ingredient.setStatus(request.status());
    }
}
