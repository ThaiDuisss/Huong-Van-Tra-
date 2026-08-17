package org.hvtra.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.*;
import org.hvtra.orderservice.domain.enums.OrderStatus;
import org.hvtra.orderservice.domain.enums.PaymentStatus;
import org.hvtra.orderservice.dto.request.CancelOrderRequest;
import org.hvtra.orderservice.dto.request.AddDraftOrderItemRequest;
import org.hvtra.orderservice.dto.request.CheckoutDraftOrderRequest;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.request.UpdateDraftOrderItemQuantityRequest;
import org.hvtra.orderservice.dto.request.UpdateOrderStatusRequest;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.dto.response.OrderStatsResponse;
import org.hvtra.orderservice.dto.response.OrderSummaryResponse;
import org.hvtra.orderservice.dto.response.TodaySalesItemResponse;
import org.hvtra.orderservice.dto.response.TodaySalesSummaryResponse;
import org.hvtra.orderservice.service.OrderExportService;
import org.hvtra.orderservice.exception.InvalidOrderStatusException;
import org.hvtra.orderservice.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final String UUID_PATH = "{orderId:[0-9a-fA-F\\-]{36}}";
    private static final DateTimeFormatter EXPORT_FILENAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final OrderService orderService;
    private final OrderExportService orderExportService;

    @Operation(summary = "checkout hoa don POS da tao draft")
    @PostMapping("/pos/checkout")
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        if (request.getOrderId() == null) {
            throw new InvalidOrderStatusException(
                    "POS checkout phải truyền orderId của hóa đơn nháp"
            );
        }
        return orderService.createOrder(request);
    }

    @Operation(summary = "them san pham vao hoa don nhap POS")
    @PostMapping("/pos/draft/items")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse addDraftItem(@Valid @RequestBody AddDraftOrderItemRequest request) {
        return orderService.addDraftItem(request);
    }

    @Operation(summary = "cap nhat so luong san pham trong hoa don nhap POS")
    @PutMapping("/pos/draft/" + UUID_PATH + "/items/{itemId}")
    public OrderResponse updateDraftItemQuantity(
            @PathVariable UUID orderId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateDraftOrderItemQuantityRequest request
    ) {
        return orderService.updateDraftItemQuantity(orderId, itemId, request);
    }

    @Operation(summary = "xoa san pham khoi hoa don nhap POS")
    @DeleteMapping("/pos/draft/" + UUID_PATH + "/items/{itemId}")
    public OrderResponse removeDraftItem(@PathVariable UUID orderId, @PathVariable UUID itemId) {
        return orderService.removeDraftItem(orderId, itemId);
    }

    @Operation(summary = "huy hoa don nhap POS")
    @PostMapping("/pos/draft/" + UUID_PATH + "/cancel")
    public OrderResponse cancelDraftOrder(@PathVariable UUID orderId) {
        return orderService.cancelDraftOrder(orderId);
    }

    @Operation(summary = "thanh toan hoa don nhap POS da ton tai")
    @PostMapping("/pos/" + UUID_PATH + "/checkout")
    public OrderResponse checkoutDraftOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody CheckoutDraftOrderRequest request
    ) {
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
    @PutMapping("/" + UUID_PATH + "/mark-paid")
    public OrderResponse markOrderPaid(
            @PathVariable UUID orderId,
            @Valid @RequestBody MarkOrderPaidRequest request
    ) {
        return orderService.markOrderPaid(orderId, request);
    }
    @GetMapping
    public Page<OrderSummaryResponse> getOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
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
                sort,
                page,
                size
        );
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

    @PutMapping("/" + UUID_PATH + "/status")
    public OrderResponse updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateOrderStatus(orderId, request);
    }

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
