package org.hvtra.orderservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Inventory cho đóng trà theo yêu cầu: tồn nguyên liệu và yêu cầu đóng gói. */
@FeignClient(
        name = "inventory-service",
        contextId = "customBundleInventoryClient",
        url = "${clients.inventory.url:${clients.inventory-service.url:}}"
)
public interface CustomBundleInventoryClient {

    @PostMapping("/internal/inventory/custom-bundles/availability")
    AvailabilityResponse checkAvailability(@RequestBody AvailabilityRequest request);

    /** Idempotent theo orderItemId. */
    @PostMapping("/internal/inventory/custom-bundles/packings")
    PackingResponse createPacking(@RequestBody CreatePackingRequest request);

    @GetMapping("/internal/inventory/custom-bundles/packings/by-order/{orderId}")
    List<PackingResponse> findPackingsByOrder(@PathVariable("orderId") UUID orderId);

    @PostMapping("/internal/inventory/custom-bundles/packings/cancel-by-order")
    List<PackingResponse> cancelPackingsByOrder(@RequestBody CancelPackingsRequest request);

    record BundleLine(UUID ingredientId, String ingredientName, BigDecimal quantity) {
    }

    record AvailabilityRequest(UUID branchId, List<BundleLine> lines) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AvailabilityResponse(boolean enough, Instant estimatedAvailableAt, Integer estimatedWaitDays) {
    }

    record CreatePackingRequest(
            UUID orderId,
            String orderCode,
            UUID orderItemId,
            UUID productVariantId,
            String bundleName,
            UUID branchId,
            List<BundleLine> lines
    ) {
    }

    record CancelPackingsRequest(UUID orderId, String reason) {
    }

    /** @param status WAITING_MATERIAL, AWAITING_PACKING, PACKED hoặc CANCELLED */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PackingResponse(
            UUID id,
            String packingCode,
            UUID orderId,
            UUID orderItemId,
            String status,
            Instant expectedAvailableAt
    ) {
    }
}
