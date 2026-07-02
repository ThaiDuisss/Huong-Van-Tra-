package org.hvtra.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.response.PaymentCheckoutResponse;
import org.hvtra.paymentservice.dto.response.OrderPaymentMethodResponse;
import org.hvtra.paymentservice.dto.response.SepayWebhookResponse;
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

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thanh toán bằng tiền mặt")
    public PaymentCheckoutResponse payByCash(@Valid @RequestBody CheckoutRequest request) {
        return paymentService.payByCash(request);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Lấy phương thức thanh toán mới nhất của đơn hàng")
    public OrderPaymentMethodResponse getPaymentByOrderId(@PathVariable UUID orderId) {
        return paymentService.getPaymentByOrderId(orderId);
    }

    @PostMapping("/sepay/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public SepayCheckoutResponse checkoutSepay(@Valid @RequestBody SepayCheckoutRequest request) {
        return paymentService.checkoutSepay(request);
    }

    @PostMapping("/webhooks/sepay")
    public SepayWebhookResponse handleSepayWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        sepayWebhookService.handle(rawBody, signature, timestamp, authorization);
        return SepayWebhookResponse.of(true);
    }
}
