package org.hvtra.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.response.CashPaymentResponse;
import org.hvtra.paymentservice.dto.response.OrderPaymentMethodResponse;
import org.hvtra.paymentservice.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/cash")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thanh toán bằng tiền mặt")
    public CashPaymentResponse payByCash(@Valid @RequestBody CheckoutRequest request) {
        return paymentService.payByCash(request);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Lấy phương thức thanh toán mới nhất của đơn hàng")
    public OrderPaymentMethodResponse getPaymentByOrderId(@PathVariable UUID orderId) {
        return paymentService.getPaymentByOrderId(orderId);
    }

    @PostMapping("/webhooks/sepay")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Webhook Sepay")
    public CashPaymentResponse handleSepayWebhook(@Valid @RequestBody CheckoutRequest request) {
//        return paymentService.payByCash(request);
        return null;
    }
}
