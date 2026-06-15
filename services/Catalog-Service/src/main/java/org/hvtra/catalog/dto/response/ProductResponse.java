package org.hvtra.catalog.dto.response;

import org.hvtra.catalog.enums.BarcodeType;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        CategoryResponse category,
        String name,
        String description,
        String imageUrl,
        Status status,
        List<VariantResponse> variants
) {
    public record VariantSummaryResponse(
            Long id,
            Long productId,
            String productName,
            String sku,
            String variantName,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            Long baseUnitId,
            InventoryPolicy inventoryPolicy,
            Status status,
            Status productStatus,
            boolean sellable
    ) {
    }

    public record VariantResponse(
            Long id,
            Long productId,
            String productName,
            String sku,
            String variantName,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            Long baseUnitId,
            InventoryPolicy inventoryPolicy,
            Status status,
            Status productStatus,
            boolean sellable,
            List<BarcodeResponse> barcodes
    ) {
    }

    public record BarcodeResponse(
            Long id,
            String barcodeValue,
            BarcodeType barcodeType,
            @com.fasterxml.jackson.annotation.JsonProperty("isPrimary") boolean primary
    ) {
    }
}
