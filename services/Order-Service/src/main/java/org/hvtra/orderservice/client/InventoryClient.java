package org.hvtra.orderservice.client;

import lombok.Builder;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Feign client gọi đến Inventory-Service để thực hiện trừ kho và lấy tồn kho.
 */
@FeignClient(name = "inventory-service", url = "${clients.inventory.url:${clients.inventory-service.url:}}")
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

    @PostMapping("/api/inventory/reservations/apply")
    void applyReservation(@RequestBody ApplyInventoryReservationRequest request);

    @PostMapping("/api/inventory/reservations/release-item")
    void releaseItem(@RequestBody ReleaseInventoryReservationItemRequest request);

    @PostMapping("/api/inventory/reservations/release-order")
    void releaseOrder(@RequestBody ReleaseInventoryReservationOrderRequest request);

    @Builder
    record ApplyInventoryReservationRequest(
            UUID orderId,
            UUID branchId,
            UUID productVariantId,
            UUID locationId,
            BigDecimal targetQuantity,
            Instant expiresAt
    ) {}

    @Builder
    record ReleaseInventoryReservationItemRequest(
            UUID orderId,
            UUID productVariantId,
            UUID locationId
    ) {}

    @Builder
    record ReleaseInventoryReservationOrderRequest(UUID orderId) {}

}
