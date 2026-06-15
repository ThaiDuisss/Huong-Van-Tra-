package org.hvtra.iventoryservice.mapper;

import org.hvtra.iventoryservice.dto.request.IngredientRequest;
import org.hvtra.iventoryservice.dto.response.IngredientResponse;
import org.hvtra.iventoryservice.entity.Ingredient;
import org.hvtra.iventoryservice.entity.Unit;
import org.springframework.stereotype.Component;

@Component
public class IngredientMapper {
    private final UnitMapper unitMapper;

    public IngredientMapper(UnitMapper unitMapper) {
        this.unitMapper = unitMapper;
    }

    public IngredientResponse toResponse(Ingredient ingredient) {
        return new IngredientResponse(
                ingredient.getId(),
                ingredient.getIngredientCode(),
                ingredient.getName(),
                unitMapper.toResponse(ingredient.getBaseUnit()),
                ingredient.getStatus()
        );
    }

    public void update(Ingredient ingredient, IngredientRequest request, Unit baseUnit) {
        ingredient.setIngredientCode(request.ingredientCode().trim());
        ingredient.setName(request.name().trim());
        ingredient.setBaseUnit(baseUnit);
        ingredient.setStatus(request.status());
    }
}
