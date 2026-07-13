package org.hvtra.orderservice.client;

import lombok.Builder;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Feign client gọi đến Inventory-Service để thực hiện trừ kho.
 *
 * TODO: Cần wire vào OrderServiceImpl khi đã xác nhận phương thức giao tiếp
 *       (REST synchronous hoặc event-driven Kafka).
 */
@FeignClient(name = "inventory-service", url = "${clients.inventory-service.url:http://localhost:8084}")
public interface InventoryClient {

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
