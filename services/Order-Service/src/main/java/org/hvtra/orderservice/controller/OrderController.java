package org.hvtra.orderservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.*;
import org.hvtra.orderservice.dto.request.CreateOrderRequest;
import org.hvtra.orderservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.orderservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.orderservice.dto.response.OrderResponse;
import org.hvtra.orderservice.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "tao don hang")
    @PostMapping("/pos/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @Operation(summary = "lay chi tiet don hang")
    @GetMapping("/{orderId}")
    public OrderResponse getOrderById(@PathVariable UUID orderId) {
        return orderService.getOrderById(orderId);
    }

    @Operation(summary = "api cho Payment-Service gọi để lấy thông tin cần thanh toán của đơn hàng")
    @GetMapping("/{orderId}/payment-info")
    public OrderPaymentInfoResponse getPaymentInfo(@PathVariable UUID orderId) {
        return orderService.getPaymentInfo(orderId);
    }

    @Operation(summary = "Api cho Payment-Service gọi sau khi thanh toán thành công để cập nhật trạng thái đơn hàng")
    @PutMapping("/{orderId}/mark-paid")
    public OrderResponse markOrderPaid(
            @PathVariable UUID orderId,
            @Valid @RequestBody MarkOrderPaidRequest request
    ) {
        return orderService.markOrderPaid(orderId, request);
    }
}