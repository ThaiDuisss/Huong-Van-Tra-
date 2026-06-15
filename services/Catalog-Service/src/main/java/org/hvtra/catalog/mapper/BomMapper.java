package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.response.BomResponse;
import org.hvtra.catalog.entity.Bom;
import org.springframework.stereotype.Component;

@Component
public class BomMapper {
    private final IngredientMapper ingredientMapper;
    private final UnitMapper unitMapper;

    public BomMapper(IngredientMapper ingredientMapper, UnitMapper unitMapper) {
        this.ingredientMapper = ingredientMapper;
        this.unitMapper = unitMapper;
    }

    public BomResponse toResponse(Bom bom) {
        return new BomResponse(
                bom.getId(),
                bom.getProductVariantId(),
                bom.getVersion(),
                bom.getStatus(),
                bom.getEffectiveFrom(),
                bom.getEffectiveTo(),
                bom.getItems().stream().map(item -> new BomResponse.ItemResponse(
                        item.getId(),
                        ingredientMapper.toResponse(item.getIngredient()),
                        item.getQuantity(),
                        unitMapper.toResponse(item.getUnit()),
                        item.getWasteRate()
                )).toList()
        );
    }
}
