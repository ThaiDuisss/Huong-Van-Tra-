package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductSummaryResponse;
import org.hvtra.catalog.entity.Barcode;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.Status;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    private final CategoryMapper categoryMapper;

    public ProductMapper(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                categoryMapper.toResponse(product.getCategory()),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getStatus(),
                product.getVariants().stream().map(this::toVariantResponse).toList()
        );
    }

    public ProductSummaryResponse toSummaryResponse(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                categoryMapper.toResponse(product.getCategory()),
                product.getImageUrl(),
                product.getStatus(),
                product.getVariants().size()
        );
    }

    public ProductResponse.VariantResponse toVariantResponse(ProductVariant variant) {
        Product product = variant.getProduct();
        return new ProductResponse.VariantResponse(
                variant.getId(),
                product.getId(),
                product.getName(),
                variant.getSku(),
                variant.getVariantName(),
                variant.getSellingPrice(),
                variant.getCostPrice(),
                variant.getBaseUnitId(),
                variant.getInventoryPolicy(),
                variant.getStatus(),
                product.getStatus(),
                isSellable(variant),
                variant.getBarcodes().stream().map(this::toBarcodeResponse).toList()
        );
    }

    public ProductResponse.BarcodeResponse toBarcodeResponse(Barcode barcode) {
        return new ProductResponse.BarcodeResponse(
                barcode.getId(),
                barcode.getBarcodeValue(),
                barcode.getBarcodeType(),
                barcode.isPrimary()
        );
    }

    public ProductResponse.VariantSummaryResponse toVariantSummary(ProductVariant variant) {
        Product product = variant.getProduct();
        return new ProductResponse.VariantSummaryResponse(
                variant.getId(), product.getId(), product.getName(), variant.getSku(),
                variant.getVariantName(), variant.getSellingPrice(), variant.getCostPrice(),
                variant.getBaseUnitId(),
                variant.getInventoryPolicy(), variant.getStatus(), product.getStatus(), isSellable(variant)
        );
    }

    private boolean isSellable(ProductVariant variant) {
        return variant.getStatus() == Status.ACTIVE && variant.getProduct().getStatus() == Status.ACTIVE;
    }
}
