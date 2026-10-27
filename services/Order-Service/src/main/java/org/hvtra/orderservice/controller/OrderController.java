package org.hvtra.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.*;
import org.hvtra.orderservice.domain.enums.OrderReturnEligibility;
import org.hvtra.orderservice.domain.enums.OrderRefundStatus;
import org.hvtra.orderservice.domain.enums.RefundProgressStatus;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.domain.enums.ReturnStatus;
import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CheckoutDraftOrderRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.CreateOrderReturnRequest;
import org.hvtra.orderservice.dto.request.CheckOrderReturnEligibilityRequest;
import org.hvtra.orderservice.dto.response.OrderReturnEligibilityResponse;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.request.SubmitReturnInventoryDispositionRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftOrderItemQuantityRequest;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderRefundDetailResponse;
import org.hvtra.orderservice.dto.response.OrderRefundStatsResponse;
import org.hvtra.orderservice.dto.response.OrderRefundSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderReturnEligibilityStatsResponse;
import org.hvtra.orderservice.dto.response.OrderReturnInventoryDispositionResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundPreviewResponse;
import org.hvtra.orderservice.dto.response.OrderReturnRefundResponse;
import org.hvtra.orderservice.dto.response.OrderReturnReportDetailResponse;
import org.hvtra.orderservice.dto.response.OrderReturnReportSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnResponse;
import org.hvtra.orderservice.dto.response.OrderReturnSummaryResponse;
import org.hvtra.orderservice.dto.response.OrderReturnTimelineResponse;
import org.hvtra.orderservice.dto.response.OrderStatsResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.service.OrderReturnReportService;
import org.hvtra.orderservice.service.OrderReturnService;
import org.hvtra.orderservice.service.OrderService;
import org.hvtra.orderservice.service.PosOfflineCheckoutService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String UUID_PATH = "{orderId:[0-9a-fA-F\\-]{36}}";
    private static final DateTimeFormatter EXPORT_FILENAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final OrderService orderService;
    private final PosOfflineCheckoutService posOfflineCheckoutService;
    private final OrderExportService orderExportService;
    private final OrderReturnService orderReturnService;
    private final OrderReturnReportService orderReturnReportService;

    /** Permission (client role Keycloak) cho phép tạo và thanh toán đơn bán tại quầy. */
    static final String PERMISSION_ORDER_CREATE = "order:create";
    /** Vai trò được chấp nhận khi gateway cũ chưa relay permission (giữ hành vi trước khi có phân quyền). */
    private static final String[] LEGACY_POS_ROLES = {"SALE_STAFF", "MANAGER", "ADMIN", "SUPER_ADMIN", "SUPERADMIN"};

    @Operation(summary = "checkout hoa don POS da tao draft")
    @UserAction(value = "ORDER_CHECKOUT", entityType = "Order", entityId = "#result.id")
    @PostMapping("/pos/checkout")
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        SecurityUtils.requirePermission(PERMISSION_ORDER_CREATE, LEGACY_POS_ROLES);
        if (request.getOrderId() == null) {
            throw new InvalidOrderStatusException(
                    "POS checkout phải truyền orderId của hóa đơn nháp"
            );
        }
        return orderService.createOrder(request);
    }

    @Operation(summary = "dong bo don da ban offline tai POS (idempotent theo clientOrderId)")
    @UserAction(value = "ORDER_OFFLINE_CHECKOUT", entityType = "Order", entityId = "#result.id")
    @PostMapping("/pos/offline-checkout")
    public OrderResponse checkoutOfflineOrder(@Valid @RequestBody CreateOrderRequest request) {
        SecurityUtils.requirePermission(PERMISSION_ORDER_CREATE, LEGACY_POS_ROLES);
        return posOfflineCheckoutService.checkout(request);
    }

    @Operation(summary = "them san pham vao hoa don nhap POS")
    @UserAction(value = "ORDER_DRAFT_ITEM_ADD", entityType = "Order", entityId = "#result.id")
    @PostMapping("/pos/draft/items")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse addDraftItem(@Valid @RequestBody AddDraftOrderItemRequest request) {
        return orderService.addDraftItem(request);
    }

    @Operation(summary = "cap nhat so luong san pham trong hoa don nhap POS")
    @UserAction(value = "ORDER_DRAFT_ITEM_UPDATE", entityType = "Order", entityId = "#orderId", detail = "#itemId")
    @PutMapping("/pos/draft/" + UUID_PATH + "/items/{itemId}")
    public OrderResponse updateDraftItemQuantity(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateDraftOrderItemQuantityRequest request
    ) {
        return orderService.updateDraftItemQuantity(orderId, itemId, request);
    }

    @Operation(summary = "xoa san pham khoi hoa don nhap POS")
    @UserAction(value = "ORDER_DRAFT_ITEM_REMOVE", entityType = "Order", entityId = "#orderId", detail = "#itemId")
    @DeleteMapping("/pos/draft/" + UUID_PATH + "/items/{itemId}")
    public OrderResponse removeDraftItem(@PathVariable UUID orderId, @PathVariable UUID itemId) {
        return orderService.removeDraftItem(orderId, itemId);
    }

    @Operation(summary = "huy hoa don nhap POS")
    @UserAction(value = "ORDER_DRAFT_CANCEL", entityType = "Order", entityId = "#orderId")
    @PostMapping("/pos/draft/" + UUID_PATH + "/cancel")
    public OrderResponse cancelDraftOrder(@PathVariable UUID orderId) {
        return orderService.cancelDraftOrder(orderId);
    }

    @Operation(summary = "huy phien thanh toan chuyen khoan dang cho, don quay ve nhap de chon lai phuong thuc")
    @UserAction(value = "ORDER_POS_PAYMENT_CANCEL", entityType = "Order", entityId = "#orderId")
    @PostMapping("/pos/" + UUID_PATH + "/payment/cancel")
    public OrderResponse cancelPosPayment(@PathVariable UUID orderId) {
        return orderService.cancelPosPayment(orderId);
    }

    @Operation(summary = "thanh toan hoa don nhap POS da ton tai")
    @UserAction(value = "ORDER_DRAFT_CHECKOUT", entityType = "Order", entityId = "#orderId")
    @PostMapping("/pos/" + UUID_PATH + "/checkout")
    public OrderResponse checkoutDraftOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody CheckoutDraftOrderRequest request
    ) {
        SecurityUtils.requirePermission(PERMISSION_ORDER_CREATE, LEGACY_POS_ROLES);
        return orderService.checkoutDraftOrder(orderId, request);
    }

    @Operation(summary = "lay chi tiet don hang")
    @GetMapping("/" + UUID_PATH)
    public OrderResponse getOrderById(@PathVariable UUID orderId) {
        return orderService.getOrderById(orderId);
    }

    @Operation(summary = "api cho Payment-Service gọi để lấy thông tin cần thanh toán của đơn hàng")
    @GetMapping("/" + UUID_PATH + "/payment-info")
    public OrderPaymentInfoResponse getPaymentInfo(@PathVariable UUID orderId) {
        return orderService.getPaymentInfo(orderId);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "businessStatus") String sort
    ) {
        byte[] excelBytes = orderExportService.exportOrders(
                keyword,
                status,
                paymentStatus,
                orderType,
                fromDate,
                toDate,
                sort
        );

        return excelResponse(excelBytes, exportFilename(
                keyword,
                status,
                paymentStatus,
                orderType,
                fromDate,
                toDate
        ));
    }

    @GetMapping("/invoices/export/excel")
    public ResponseEntity<byte[]> exportInvoices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "invoicePayment") String sort
    ) {
        byte[] excelBytes = orderExportService.exportInvoices(
                keyword,
                paymentStatus,
                fromDate,
                toDate,
                sort
        );

        return excelResponse(
                excelBytes,
                invoiceExportFilename(keyword, paymentStatus, fromDate, toDate)
        );
    }

    @Operation(summary = "Api cho Payment-Service gọi sau khi thanh toán thành công để cập nhật trạng thái đơn hàng")
    @UserAction(value = "ORDER_MARK_PAID", entityType = "Order", entityId = "#orderId")
    @PutMapping("/" + UUID_PATH + "/mark-paid")
    public OrderResponse markOrderPaid(
            @PathVariable UUID orderId,
            @Valid @RequestBody MarkOrderPaidRequest request
    ) {
        return orderService.markOrderPaid(orderId, request);
    }

    @Operation(summary = "kiem tra dieu kien va chinh sach hoan hang truoc khi tao phieu")
    @PostMapping("/" + UUID_PATH + "/returns/eligibility")
    public OrderReturnEligibilityResponse checkReturnEligibility(
            @PathVariable UUID orderId,
            @Valid @RequestBody CheckOrderReturnEligibilityRequest request
    ) {
        return orderReturnService.checkReturnEligibility(orderId, request);
    }

    @Operation(summary = "tao yeu cau hoan hang cho don hang")
    @PostMapping("/" + UUID_PATH + "/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderReturnResponse createReturn(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateOrderReturnRequest request
    ) {
        return orderReturnService.createReturn(orderId, request);
    }

    @Operation(summary = "lay danh sach hoan hang cua don hang")
    @GetMapping("/" + UUID_PATH + "/returns")
    public List<OrderReturnResponse> getReturnsByOrder(@PathVariable UUID orderId) {
        return orderReturnService.getReturnsByOrder(orderId);
    }

    @Operation(summary = "lay danh sach phieu hoan hang toan he thong")
    @GetMapping("/returns")
    public Page<OrderReturnSummaryResponse> getReturns(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String returnCode,
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String displayStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean paged,
            @RequestParam(defaultValue = "false") boolean warehouseActiveOnly
    ) {
        String effectiveDisplayStatus = displayStatus != null && !displayStatus.isBlank() ? displayStatus : status;
        ReturnStatus rawStatus = null;
        if (effectiveDisplayStatus != null) {
            try {
                rawStatus = ReturnStatus.valueOf(effectiveDisplayStatus.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Not a raw ReturnStatus enum, will be handled as displayStatus
            }
        }
        if (paged || returnCode != null || orderCode != null) {
            return orderReturnService.getFilteredReturns(
                    keyword, returnCode, orderCode, rawStatus, effectiveDisplayStatus,
                    fromDate, toDate, sort, page, size, warehouseActiveOnly
            );
        }
        return orderReturnService.getReturns(keyword, rawStatus, effectiveDisplayStatus, fromDate, toDate, sort, page, size, warehouseActiveOnly);
    }

    public Page<OrderReturnSummaryResponse> getReturns(
            String keyword,
            ReturnStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            String sort,
            int page,
            int size
    ) {
        return orderReturnService.getReturns(keyword, status, null, fromDate, toDate, sort, page, size, false);
    }

    @Operation(summary = "thong ke so luong phieu hoan theo trang thai hien thi")
    @GetMapping("/returns/status-counts")
    public Map<String, Long> getReturnStatusCounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "false") boolean warehouseActiveOnly
    ) {
        return orderReturnService.getReturnStatusCounts(keyword, fromDate, toDate, warehouseActiveOnly);
    }

    @Operation(summary = "lay danh sach hoan tien tu phieu hoan hang")
    @GetMapping("/refunds")
    public Page<OrderRefundSummaryResponse> getRefunds(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderRefundStatus status,
            @RequestParam(required = false) RefundProgressStatus progressStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return orderReturnService.getRefunds(keyword, status, progressStatus, fromDate, toDate, sort, page, size);
    }

    @Operation(summary = "thong ke trang thai hoan tien")
    @GetMapping("/refunds/stats")
    public OrderRefundStatsResponse getRefundStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return orderReturnService.getRefundStats(keyword, fromDate, toDate);
    }

    @Operation(summary = "lay chi tiet hoan tien")
    @GetMapping("/refunds/{returnId:[0-9a-fA-F\\-]{36}}")
    public OrderRefundDetailResponse getRefundDetail(@PathVariable UUID returnId) {
        return orderReturnService.getRefundDetail(returnId);
    }

    @Operation(summary = "lay chi tiet hoan hang")
    @GetMapping("/returns/{returnId:[0-9a-fA-F\\-]{36}}")
    public OrderReturnResponse getReturnById(@PathVariable UUID returnId) {
        return orderReturnService.getReturnById(returnId);
    }

    @Operation(summary = "lay timeline xu ly hoan hang")
    @GetMapping("/returns/{returnId:[0-9a-fA-F\\-]{36}}/timeline")
    public List<OrderReturnTimelineResponse> getReturnTimeline(@PathVariable UUID returnId) {
        return orderReturnService.getReturnTimeline(returnId);
    }

    @Operation(summary = "bao cao tong hop hoan hang va hoan tien")
    @GetMapping("/reports/returns/summary")
    public OrderReturnReportSummaryResponse getReturnReportSummary(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return orderReturnReportService.getSummary(branchId, fromDate, toDate);
    }

    @Operation(summary = "bao cao chi tiet hoan hang va hoan tien")
    @GetMapping("/reports/returns")
    public Page<OrderReturnReportDetailResponse> getReturnReportDetails(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ReturnStatus returnStatus,
            @RequestParam(required = false) String refundStatus,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return orderReturnReportService.getDetails(
                keyword,
                returnStatus,
                refundStatus,
                branchId,
                fromDate,
                toDate,
                sort,
                page,
                size
        );
    }

    @Operation(summary = "xem truoc so tien hoan tien")
    @GetMapping("/returns/{returnId:[0-9a-fA-F\\-]{36}}/refund-preview")
    public OrderReturnRefundPreviewResponse getRefundPreview(@PathVariable UUID returnId) {
        return orderReturnService.getRefundPreview(returnId);
    }

    @Operation(summary = "xac nhan cua hang da nhan hang tra lai")
    @PutMapping("/returns/{returnId:[0-9a-fA-F\\-]{36}}/receive")
    public OrderReturnResponse receiveReturn(@PathVariable UUID returnId) {
        return orderReturnService.receiveReturn(returnId);
    }

    @Operation(summary = "huy phieu hoan hang chua hoan tat")
    @PutMapping("/returns/{returnId:[0-9a-fA-F\\-]{36}}/cancel")
    public OrderReturnResponse cancelReturn(@PathVariable UUID returnId) {
        return orderReturnService.cancelReturn(returnId);
    }

    @Operation(summary = "hoan tien cho phieu hoan hang")
    @PostMapping("/returns/{returnId:[0-9a-fA-F\\-]{36}}/refund")
    public OrderReturnRefundResponse refundReturn(@PathVariable UUID returnId) {
        return orderReturnService.refundReturn(returnId);
    }

    @Operation(summary = "luu lenh xu ly ton kho cho phieu hoan hang")
    @PostMapping("/returns/{returnId:[0-9a-fA-F\\-]{36}}/inventory-disposition")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderReturnInventoryDispositionResponse submitReturnInventoryDisposition(
            @PathVariable UUID returnId,
            @Valid @RequestBody SubmitReturnInventoryDispositionRequest request
    ) {
        Set<String> allowed = Set.of("QC", "QC_STAFF", "MANAGER", "ADMIN", "SUPER_ADMIN",
                "BRANCH_MANAGER", "INVENTORY_MANAGER");
        if (SecurityUtils.currentRoles().stream()
                .map(role -> role.trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", ""))
                .noneMatch(allowed::contains)) {
            throw new AccessDeniedException("Customer return disposition requires QC or manager role");
        }
        return orderReturnService.submitInventoryDisposition(returnId, request);
    }

    @Operation(summary = "lay trang thai lenh xu ly ton kho cua phieu hoan hang")
    @GetMapping("/returns/{returnId:[0-9a-fA-F\\-]{36}}/inventory-dispositions/{commandId:[0-9a-fA-F\\-]{36}}")
    public OrderReturnInventoryDispositionResponse getReturnInventoryDisposition(
            @PathVariable UUID returnId,
            @PathVariable UUID commandId
    ) {
        return orderReturnService.getInventoryDisposition(returnId, commandId);
    }

    @GetMapping
    public Page<OrderSummaryResponse> getOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) OrderReturnEligibility returnEligibility,
            @RequestParam(defaultValue = "businessStatus") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return orderService.getOrders(
                keyword,
                status,
                paymentStatus,
                orderType,
                fromDate,
                toDate,
                returnEligibility,
                sort,
                page,
                size
        );
    }

    @GetMapping("/return-eligibility-stats")
    public OrderReturnEligibilityStatsResponse getOrderReturnEligibilityStats(
            @RequestParam(required = false) String keyword
    ) {
        return orderService.getOrderReturnEligibilityStats(keyword);
    }

    @GetMapping("/today-sales")
    public Page<TodaySalesItemResponse> getTodaySales(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return orderService.getTodaySales(status, paymentStatus, paymentMethod, page, size);
    }

    @GetMapping("/today-sales/summary")
    public TodaySalesSummaryResponse getTodaySalesSummary() {
        return orderService.getTodaySalesSummary();
    }

    @GetMapping("/stats")
    public OrderStatsResponse getOrderStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return orderService.getOrderStats(fromDate, toDate);
    }

    @UserAction(value = "ORDER_STATUS_UPDATE", entityType = "Order", entityId = "#orderId", detail = "#request.status")
    @PutMapping("/" + UUID_PATH + "/status")
    public OrderResponse updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateOrderStatus(orderId, request);
    }

    @UserAction(value = "ORDER_CANCEL", entityType = "Order", entityId = "#orderId", detail = "#request.reason")
    @PutMapping("/" + UUID_PATH + "/cancel")
    public OrderResponse cancelOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        return orderService.cancelOrder(orderId, request);
    }

    private String exportFilename(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        String prefix = hasExportFilter(keyword, status, paymentStatus, orderType, fromDate, toDate)
                ? "don-hang-theo-bo-loc"
                : "tat-ca-don-hang";

        return prefix + "-" + LocalDateTime.now().format(EXPORT_FILENAME_TIME) + ".xlsx";
    }

    private String invoiceExportFilename(
            String keyword,
            PaymentStatus paymentStatus,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        boolean hasFilter = (keyword != null && !keyword.isBlank())
                || paymentStatus != null
                || fromDate != null
                || toDate != null;
        String prefix = hasFilter ? "hoa-don-theo-bo-loc" : "tat-ca-hoa-don";
        return prefix + "-" + LocalDateTime.now().format(EXPORT_FILENAME_TIME) + ".xlsx";
    }

    private ResponseEntity<byte[]> excelResponse(byte[] excelBytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .body(excelBytes);
    }

    private boolean hasExportFilter(
            String keyword,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String orderType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (keyword != null && !keyword.isBlank())
                || status != null
                || paymentStatus != null
                || (orderType != null && !orderType.isBlank())
                || fromDate != null
                || toDate != null;
    }
}
