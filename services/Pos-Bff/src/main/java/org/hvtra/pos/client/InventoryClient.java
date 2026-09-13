package org.hvtra.pos.client;

import lombok.Builder;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "inventory-service", url = "${clients.inventory.url:}")
public interface InventoryClient {

    @PostMapping("/internal/inventory/stocks/variants/batch")
    List<StockVariantBatchResponse> getStocksByVariantIds(@RequestBody StockVariantBatchRequest request);

    @Builder
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
