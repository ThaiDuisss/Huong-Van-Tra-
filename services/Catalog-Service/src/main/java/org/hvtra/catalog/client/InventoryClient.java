package org.hvtra.catalog.client;

import lombok.Builder;
import org.hvtra.catalog.enums.Status;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "inventory-service", url = "${clients.inventory.url:}")
public interface InventoryClient {
    @PostMapping("/api/inventory/inventory-items")
    void createProductVariantItem(
            @RequestBody CreateInventoryItemRequest request
    );

    @PostMapping("/internal/inventory/stocks/batch")
    List<StockBatchResponse> getStocksByProductIds(@RequestBody StockBatchRequest request);

    @Builder
    record CreateInventoryItemRequest(
            UUID referenceId,
            String itemType,
            UUID unitId,
            String status,
            Integer reorderPoint
    ) {
    }

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
}
