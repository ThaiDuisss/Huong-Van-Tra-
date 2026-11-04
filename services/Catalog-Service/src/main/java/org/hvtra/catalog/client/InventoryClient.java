package org.hvtra.catalog.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "inventory-service", url = "${clients.inventory.url:}")
public interface InventoryClient {

    @PostMapping("/internal/inventory/stocks/batch")
    List<StockBatchResponse> getStocksByProductIds(@RequestBody StockBatchRequest request);

    @PostMapping("/internal/inventory/stocks/variants/batch")
    List<StockVariantBatchResponse> getStocksByVariantIds(@RequestBody StockVariantBatchRequest request);

    record StockBatchRequest(
            List<UUID> productIds,
            UUID warehouseId
    ) {
        public StockBatchRequest(List<UUID> productIds) {
            this(productIds, null);
        }
    }

    record StockBatchResponse(
            UUID productId,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            String stockStatus
    ) {
    }

    record StockVariantBatchRequest(
            List<UUID> variantIds,
            UUID warehouseId
    ) {
        public StockVariantBatchRequest(List<UUID> variantIds) {
            this(variantIds, null);
        }
    }

    record StockVariantBatchResponse(
            UUID variantId,
            UUID inventoryLocationId,
            BigDecimal availableQuantity,
            BigDecimal reservedQuantity,
            String stockStatus
    ) {
    }
}
