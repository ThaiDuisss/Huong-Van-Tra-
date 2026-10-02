package org.hvtra.orderservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import org.hvtra.orderservice.domain.enums.ReturnCondition;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.response.ReturnDispositionStatusResponse;
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
            String stockStatus,
            BigDecimal shelfQuantity,
            BigDecimal warehouseQuantity,
            UUID shelfLocationId,
            UUID warehouseLocationId
    ) {
    }

    @PostMapping("/api/inventory/reservations/apply")
    void applyReservation(@RequestBody ApplyInventoryReservationRequest request);

    @PostMapping("/api/inventory/reservations/release-item")
    void releaseItem(@RequestBody ReleaseInventoryReservationItemRequest request);

    @PostMapping("/api/inventory/reservations/release-order")
    void releaseOrder(@RequestBody ReleaseInventoryReservationOrderRequest request);

    @PostMapping("/api/inventory/reservations/materials/reserve-order")
    void reserveMaterials(@RequestBody ReserveMaterialOrderRequest request);

    @PostMapping("/api/inventory/reservations/materials/consume-order")
    void consumeMaterials(@RequestBody ReleaseInventoryReservationOrderRequest request);

    @PostMapping("/api/inventory/reservations/materials/release-order")
    List<MaterialReservationResponse> releaseMaterials(@RequestBody ReleaseInventoryReservationOrderRequest request);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MaterialReservationResponse(String status) {}

    /**
     * Xử lý tồn kho cho đơn giao ngay tại quầy trong một transaction của Inventory: xuất kho bù kệ nếu thiếu,
     * trừ thành phẩm trên kệ theo FEFO, giữ chỗ nguyên liệu cho việc sản xuất bù. Gọi lại không trừ lần hai.
     */
    @PostMapping("/internal/inventory/sales/fulfill")
    FulfillSaleResponse fulfillSale(@RequestBody FulfillSaleRequest request);

    @PostMapping("/internal/inventory/production-alerts")
    void createProductionRequiredAlert(@RequestBody ProductionRequiredAlertRequest request);

    record ProductionRequiredAlertRequest(UUID variantId, UUID branchId, BigDecimal sellableQuantity,
                                          BigDecimal productionThreshold, BigDecimal productionBatchQuantity) {}

    record ReserveMaterialOrderRequest(UUID orderId, UUID branchId, int processingAttempt,
                                       Instant expiresAt, List<ReserveMaterialLineRequest> lines) {}

    record ReserveMaterialLineRequest(UUID orderItemId, UUID productVariantId, UUID ingredientId,
                                      UUID inventoryItemId, UUID locationId, BigDecimal quantity) {}

    @GetMapping("/api/inventory/customer-returns/{returnId}/qc-summary")
    CustomerReturnQcSummaryResponse getCustomerReturnQcSummary(@PathVariable("returnId") UUID returnId);

    @PostMapping("/api/inventory/customer-returns/qc-summaries")
    List<CustomerReturnQcSummaryResponse> getCustomerReturnQcSummaries(
            @RequestBody CustomerReturnQcSummaryBatchRequest request
    );

    @PostMapping("/api/inventory/customer-returns/qc-disposition-report")
    List<CustomerReturnQcDispositionReportResponse> getCustomerReturnQcDispositionReport(
            @RequestBody CustomerReturnQcDispositionReportRequest request
    );

    @GetMapping("/api/inventory/internal/return-dispositions/{commandId}/status")
    ReturnDispositionStatusResponse getReturnDispositionStatus(@PathVariable("commandId") UUID commandId);

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
            UUID branchId,
            UUID productVariantId,
            UUID locationId,
            BigDecimal quantity
    ) {}

    @Builder
    record ReleaseInventoryReservationOrderRequest(UUID orderId) {}

    record CustomerReturnQcSummaryBatchRequest(
            List<CustomerReturnSnapshot> returns
    ) {}

    record CustomerReturnSnapshot(
            UUID returnId,
            String returnCode,
            ReturnStatus returnStatus,
            UUID branchId,
            UUID orderId,
            String orderCode,
            List<CustomerReturnLineSnapshot> lines
    ) {}

    record CustomerReturnLineSnapshot(
            UUID returnItemId,
            UUID orderItemId,
            UUID productVariantId,
            BigDecimal quantity,
            ReturnCondition condition,
            String reason
    ) {}

    record CustomerReturnQcDispositionReportRequest(
            List<UUID> returnIds
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CustomerReturnQcSummaryResponse(
            UUID returnId,
            String returnCode,
            String returnStatus,
            UUID branchId,
            UUID orderId,
            String orderCode,
            UUID inspectionId,
            String inspectionStatus,
            String qcStatus,
            List<CustomerReturnQcLineResponse> lines,
            Instant inspectedAt
    ) {
        public CustomerReturnQcSummaryResponse(
                UUID returnId,
                String returnCode,
                String returnStatus,
                UUID branchId,
                UUID orderId,
                String orderCode,
                UUID inspectionId,
                String inspectionStatus,
                String qcStatus,
                List<CustomerReturnQcLineResponse> lines
        ) {
            this(returnId, returnCode, returnStatus, branchId, orderId, orderCode, inspectionId, inspectionStatus, qcStatus, lines, null);
        }

        public CustomerReturnQcSummaryResponse(
                UUID returnId,
                String returnCode,
                String returnStatus,
                UUID branchId,
                UUID orderId,
                String orderCode,
                String qcStatus,
                List<CustomerReturnQcLineResponse> lines
        ) {
            this(returnId, returnCode, returnStatus, branchId, orderId, orderCode, null, null, qcStatus, lines, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CustomerReturnQcLineResponse(
            UUID returnItemId,
            UUID orderItemId,
            UUID productVariantId,
            BigDecimal returnedQuantity,
            BigDecimal postedQcQuantity,
            BigDecimal remainingQcQuantity,
            String status
    ) {
        public CustomerReturnQcLineResponse(
                UUID returnItemId,
                UUID orderItemId,
                BigDecimal returnedQuantity,
                BigDecimal inspectedQuantity,
                BigDecimal acceptedQuantity,
                BigDecimal rejectedQuantity,
                String qcStatus
        ) {
            this(returnItemId, orderItemId, (UUID) null, returnedQuantity, inspectedQuantity, BigDecimal.ZERO, qcStatus);
        }
    }

    record CustomerReturnQcDispositionReportResponse(
            String result,
            BigDecimal quantity
    ) {}


    record FulfillSaleRequest(
            UUID orderId,
            UUID branchId,
            int processingAttempt,
            List<FulfillSaleLine> lines,
            List<FulfillSaleMaterialLine> materials
    ) {
    }

    record FulfillSaleLine(UUID productVariantId, BigDecimal quantity) {
    }

    record FulfillSaleMaterialLine(UUID productVariantId, UUID ingredientId, BigDecimal quantity) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record FulfillSaleResponse(
            UUID orderId,
            boolean alreadyProcessed,
            String replenishmentCode,
            List<FulfillSaleLineResult> lines
    ) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record FulfillSaleLineResult(
            UUID productVariantId,
            BigDecimal soldQuantity,
            BigDecimal movedFromWarehouse,
            BigDecimal remainingShelfQuantity,
            BigDecimal remainingWarehouseQuantity
    ) {
    }
}
