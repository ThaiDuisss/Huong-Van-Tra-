package org.hvtra.orderservice.domain.controller;

import jakarta.validation.Valid;
import lombok.*;
import org.hvtra.orderservice.domain.DTO.request.*;
import org.hvtra.orderservice.domain.DTO.response.*;
import org.hvtra.orderservice.domain.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // Tạo đơn hàng mới từ màn POS
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    // Lấy chi tiết đơn hàng theo orderId
    @GetMapping("/{orderId}")
    public OrderResponse getOrderById(@PathVariable UUID orderId) {
        return orderService.getOrderById(orderId);
    }

    // API cho Payment-Service gọi để lấy thông tin cần thanh toán của đơn hàng
    @GetMapping("/{orderId}/payment-info")
    public OrderPaymentInfoResponse getPaymentInfo(@PathVariable UUID orderId) {
        return orderService.getPaymentInfo(orderId);
    }

    // API cho Payment-Service gọi sau khi thanh toán thành công
    // Order-Service chỉ cập nhật trạng thái đơn, không lưu dữ liệu payment
    @PutMapping("/{orderId}/mark-paid")
    public OrderResponse markOrderPaid(
            @PathVariable UUID orderId,
            @Valid @RequestBody MarkOrderPaidRequest request
    ) {
        return orderService.markOrderPaid(orderId, request);
    }
}