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
        String sku = bom.getProductVariant() != null ? bom.getProductVariant().getSku() : null;
        String variantName = bom.getProductVariant() != null ? bom.getProductVariant().getVariantName() : null;
        String productName = bom.getProductVariant() != null && bom.getProductVariant().getProduct() != null
                ? bom.getProductVariant().getProduct().getName() : null;

        return new BomResponse(
                bom.getId(),
                bom.getProductVariantId(),
                sku,
                variantName,
                productName,
                bom.getName(),
                bom.getBomCode(),
                bom.isDefault(),
                bom.getBomVersion(),
                bom.getStatus(),
                bom.getDescription(),
                bom.getEffectiveFrom(),
                bom.getEffectiveTo(),
                bom.getCreatedAt(),
                bom.getUpdatedAt(),
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
