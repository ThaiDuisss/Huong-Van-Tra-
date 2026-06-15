package org.hvtra.iventoryservice.mapper;

import org.hvtra.iventoryservice.dto.response.BomResponse;
import org.hvtra.iventoryservice.entity.Bom;
import org.springframework.stereotype.Component;

@Component
public class BomMapper {
    private final ProductMapper productMapper;
    private final IngredientMapper ingredientMapper;
    private final UnitMapper unitMapper;

    public BomMapper(ProductMapper productMapper, IngredientMapper ingredientMapper, UnitMapper unitMapper) {
        this.productMapper = productMapper;
        this.ingredientMapper = ingredientMapper;
        this.unitMapper = unitMapper;
    }

    public BomResponse toResponse(Bom bom) {
        return new BomResponse(
                bom.getId(),
                productMapper.toVariantSummary(bom.getProductVariant()),
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
