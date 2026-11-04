package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        CategoryResponse category,
        String productCode,
        String name,
        String description,
        String imageUrl,
        Status status,
        List<VariantResponse> variants
) {
    public record VariantSummaryResponse(
            UUID id,
            UUID productId,
            String productName,
            String productCode,
            String sku,
            String variantName,
            BigDecimal quantity,
            String categoryName,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            BigDecimal sellableQuantity,
            BigDecimal productionThreshold,
            BigDecimal productionBatchQuantity,
            String imageUrl,
            UUID unitId,
            String unitName,
            String unitSymbol,
            InventoryPolicy inventoryPolicy,
            Status status,
            Status productStatus,
            boolean sellable,
            String barcode
    ) {
    }

    public record VariantResponse(
            UUID id,
            UUID productId,
            String productName,
            String productCode,
            String sku,
            String variantName,
            BigDecimal quantity,
            String categoryName,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            BigDecimal sellableQuantity,
            BigDecimal productionThreshold,
            BigDecimal productionBatchQuantity,
            String imageUrl,
            UUID unitId,
            String unitName,
            String unitSymbol,
            InventoryPolicy inventoryPolicy,
            Status status,
            Status productStatus,
            boolean sellable,
            String barcode
    ) {
    }
}

