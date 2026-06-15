package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.response.PriceListResponse;
import org.hvtra.catalog.entity.PriceList;
import org.springframework.stereotype.Component;

@Component
public class PriceListMapper {
    private final ProductMapper productMapper;

    public PriceListMapper(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public PriceListResponse toResponse(PriceList priceList) {
        return new PriceListResponse(
                priceList.getId(),
                priceList.getBranchId(),
                priceList.getName(),
                priceList.getEffectiveFrom(),
                priceList.getEffectiveTo(),
                priceList.getStatus(),
                priceList.getItems().stream().map(item -> new PriceListResponse.ItemResponse(
                        item.getId(),
                        productMapper.toVariantSummary(item.getProductVariant()),
                        item.getSellingPrice()
                )).toList()
        );
    }
}
