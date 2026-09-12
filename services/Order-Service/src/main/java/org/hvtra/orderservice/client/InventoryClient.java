package org.hvtra.orderservice.client;

import lombok.Builder;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping("/api/inventory/reservations/apply")
    void applyReservation(@RequestBody ApplyInventoryReservationRequest request);

    @PostMapping("/api/inventory/reservations/release-item")
    void releaseItem(@RequestBody ReleaseInventoryReservationItemRequest request);

    @PostMapping("/api/inventory/reservations/release-order")
    void releaseOrder(@RequestBody ReleaseInventoryReservationOrderRequest request);

    @GetMapping("/api/inventory/customer-returns/{returnId}/qc-summary")
    CustomerReturnQcSummaryResponse getCustomerReturnQcSummary(@PathVariable("returnId") UUID returnId);

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

    record CustomerReturnQcSummaryResponse(
            UUID returnId,
            String returnCode,
            String returnStatus,
            UUID branchId,
            UUID orderId,
            String orderCode,
            String qcStatus,
            List<CustomerReturnQcLineResponse> lines
    ) {}

    record CustomerReturnQcLineResponse(
            UUID returnItemId,
            UUID orderItemId,
            BigDecimal returnedQuantity,
            BigDecimal inspectedQuantity,
            BigDecimal acceptedQuantity,
            BigDecimal rejectedQuantity,
            String qcStatus
    ) {}

}
