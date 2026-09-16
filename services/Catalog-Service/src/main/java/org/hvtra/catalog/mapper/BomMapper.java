package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.response.BomLookupResponse;
import org.hvtra.catalog.dto.response.BomResponse;
import org.hvtra.catalog.dto.response.IngredientResponse;
import org.hvtra.catalog.dto.response.UnitResponse;
import org.hvtra.catalog.entity.Bom;
import org.hvtra.catalog.entity.BomItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BomMapper {
    private final IngredientMapper ingredientMapper;
    private final UnitMapper unitMapper;

    public BomMapper(IngredientMapper ingredientMapper, UnitMapper unitMapper) {
        this.ingredientMapper = ingredientMapper;
        this.unitMapper = unitMapper;
    }

    public BomResponse toResponse(Bom bom) {
        return toResponse(bom, bom.getItems());
    }

    public BomResponse toResponse(Bom bom, List<BomItem> items) {
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
                items == null ? java.util.Collections.emptyList() : items.stream().map(item -> {
                    IngredientResponse ing = null;
                    try {
                        ing = item.getIngredient() != null ? ingredientMapper.toResponse(item.getIngredient()) : null;
                    } catch (Exception ignored) {
                    }
                    UnitResponse unit = null;
                    try {
                        unit = item.getUnit() != null ? unitMapper.toResponse(item.getUnit()) : null;
                    } catch (Exception ignored) {
                    }
                    return new BomResponse.ItemResponse(
                            item.getId(),
                            ing,
                            item.getQuantity(),
                            unit,
                            item.getWasteRate()
                    );
                }).toList()
        );
    }

    public BomLookupResponse toLookupResponse(Bom bom) {
        return new BomLookupResponse(
                bom.getId(),
                bom.getBomCode(),
                bom.getName(),
                bom.isDefault(),
                bom.getBomVersion(),
                bom.getStatus()
        );
    }
}
