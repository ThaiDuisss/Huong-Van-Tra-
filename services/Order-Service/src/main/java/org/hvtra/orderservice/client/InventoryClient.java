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
 * Feign client gọi đến Inventory-Service để thực hiện trừ kho.
 *
 * TODO: Cần wire vào OrderServiceImpl khi đã xác nhận phương thức giao tiếp
 *       (REST synchronous hoặc event-driven Kafka).
 */
@FeignClient(name = "inventory-service", url = "${clients.inventory-service.url:http://localhost:8084}")
public interface InventoryClient {

    /**
     * Trừ kho nguyên liệu theo công thức BOM khi đơn hàng hoàn thành.
     */
    @PostMapping("/api/inventory/stock-out/by-bom")
    void deductStockByBom(@RequestBody DeductStockByBomRequest request);

    @PostMapping("/api/inventory/reservations/apply")
    void applyReservation(@RequestBody ApplyInventoryReservationRequest request);

    @PostMapping("/api/inventory/reservations/release-item")
    void releaseItem(@RequestBody ReleaseInventoryReservationItemRequest request);

    @PostMapping("/api/inventory/reservations/release-order")
    void releaseOrder(@RequestBody ReleaseInventoryReservationOrderRequest request);

    @PostMapping("/api/inventory/reservations/confirm-order")
    void confirmOrder(@RequestBody ConfirmInventoryReservationOrderRequest request);

    @Builder
    record DeductStockByBomRequest(
            UUID orderId,
            String referenceType,
            UUID branchId,
            List<BomDeductionLine> lines
    ) {}

    @Builder
    record BomDeductionLine(
            /** ID của InventoryItem tương ứng với nguyên liệu */
            UUID inventoryItemId,
            /** ID kho cần trừ */
            UUID locationId,
            /** Số lượng nguyên liệu cần trừ (đã nhân với tỷ lệ chuyển đổi đơn vị nếu cần) */
            BigDecimal quantity
    ) {}

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

    @Builder
    record ConfirmInventoryReservationOrderRequest(UUID orderId) {}
}
