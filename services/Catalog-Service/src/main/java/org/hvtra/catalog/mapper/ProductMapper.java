package org.hvtra.catalog.mapper;

import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.dto.response.ProductSummaryResponse;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.Status;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ProductMapper {
    private final CategoryMapper categoryMapper;

    public ProductMapper(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public ProductResponse toResponse(Product product) {
        return toResponse(product, product.getVariants());
    }

    public ProductResponse toResponse(Product product, List<ProductVariant> variants) {
        List<ProductVariant> activeVariants = variants == null ? List.of() :
                variants.stream().filter(v -> !Boolean.TRUE.equals(v.getIsDeleted())).toList();
        return new ProductResponse(
                product.getId(),
                categoryMapper.toResponse(product.getCategory()),
                product.getProductCode(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getStatus(),
                activeVariants.stream().map(this::toVariantResponse).toList()
        );
    }

    public ProductSummaryResponse toSummaryResponse(Product product) {
        return toSummaryResponse(product, BigDecimal.ZERO, BigDecimal.ZERO, "OUT_OF_STOCK");
    }

    public ProductSummaryResponse toSummaryResponse(
            Product product,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            String stockStatus) {
        BigDecimal safeAvailable = availableQuantity != null ? availableQuantity : BigDecimal.ZERO;
        BigDecimal safeReserved = reservedQuantity != null ? reservedQuantity : BigDecimal.ZERO;
        String safeStatus = stockStatus != null && !stockStatus.isBlank() ? stockStatus : "OUT_OF_STOCK";
        return new ProductSummaryResponse(
                product.getId(),
                product.getProductCode(),
                product.getName(),
                categoryMapper.toResponse(product.getCategory()),
                product.getImageUrl(),
                product.getStatus(),
                product.getVariants() == null ? 0 : (int) product.getVariants().stream().filter(v -> !Boolean.TRUE.equals(v.getIsDeleted())).count(),
                safeAvailable,
                safeReserved,
                safeStatus,
                safeAvailable.compareTo(BigDecimal.ZERO) > 0
        );
    }

    public ProductResponse.VariantResponse toVariantResponse(ProductVariant variant) {
        Product product = variant.getProduct();
        String categoryName = (product != null && product.getCategory() != null) ? product.getCategory().getName() : null;
        String imageUrl = product != null ? product.getImageUrl() : null;
        return new ProductResponse.VariantResponse(
                variant.getId(),
                product != null ? product.getId() : null,
                product != null ? product.getName() : null,
                product != null ? product.getProductCode() : null,
                variant.getSku(),
                variant.getVariantName(),
                variant.getQuantity(),
                categoryName,
                variant.getSellingPrice(),
                variant.getCostPrice(),
                variant.getSellableQuantity(),
                variant.getProductionThreshold(),
                variant.getProductionBatchQuantity(),
                imageUrl,
                variant.getUnit() != null ? variant.getUnit().getId() : null,
                variant.getUnit() != null ? variant.getUnit().getName() : null,
                variant.getUnit() != null ? variant.getUnit().getSymbol() : null,
                variant.getInventoryPolicy(),
                variant.getStatus(),
                product != null ? product.getStatus() : null,
                isSellable(variant),
                variant.getBarcode());
    }

    public ProductResponse.VariantResponse toVariantResponseForNewlyCreated(ProductVariant variant) {
        return toVariantResponse(variant);
    }

    public ProductResponse.VariantSummaryResponse toVariantSummary(ProductVariant variant) {
        Product product = variant.getProduct();
        String categoryName = (product != null && product.getCategory() != null) ? product.getCategory().getName() : null;
        String imageUrl = product != null ? product.getImageUrl() : null;
        return new ProductResponse.VariantSummaryResponse(
                variant.getId(),
                product != null ? product.getId() : null,
                product != null ? product.getName() : null,
                product != null ? product.getProductCode() : null,
                variant.getSku(),
                variant.getVariantName(),
                variant.getQuantity(),
                categoryName,
                variant.getSellingPrice(),
                variant.getCostPrice(),
                variant.getSellableQuantity(),
                variant.getProductionThreshold(),
                variant.getProductionBatchQuantity(),
                imageUrl,
                variant.getUnit() != null ? variant.getUnit().getId() : null,
                variant.getUnit() != null ? variant.getUnit().getName() : null,
                variant.getUnit() != null ? variant.getUnit().getSymbol() : null,
                variant.getInventoryPolicy(),
                variant.getStatus(),
                product != null ? product.getStatus() : null,
                isSellable(variant),
                variant.getBarcode()
        );
    }

    private boolean isSellable(ProductVariant variant) {
        return variant.getStatus() == Status.ACTIVE && variant.getProduct().getStatus() == Status.ACTIVE;
    }
}
