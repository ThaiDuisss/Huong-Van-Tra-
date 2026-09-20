package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.response.BomItemResponse;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.entity.BomItem;
import org.springframework.stereotype.Component;

@Component
public class BomItemMapper {
    private final IngredientMapper ingredientMapper;
    private final UnitMapper unitMapper;

    public BomItemMapper(IngredientMapper ingredientMapper, UnitMapper unitMapper) {
        this.ingredientMapper = ingredientMapper;
        this.unitMapper = unitMapper;
    }

    public BomItemResponse toResponse(BomItem item) {
        IngredientResponse ingredient = null;
        try {
            ingredient = item.getIngredient() != null ? ingredientMapper.toResponse(item.getIngredient()) : null;
        } catch (Exception ignored) {
        }
        UnitResponse unit = null;
        try {
            unit = item.getUnit() != null ? unitMapper.toResponse(item.getUnit()) : null;
        } catch (Exception ignored) {
        }
        return new BomItemResponse(
                item.getId(),
                ingredient,
                item.getQuantity(),
                unit,
                item.getWasteRate()
        );
    }
}
