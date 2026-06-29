package org.hvtra.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.dto.request.CheckoutRequest;
import org.hvtra.paymentservice.dto.response.CashPaymentResponse;
import org.hvtra.paymentservice.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public CashPaymentResponse payByCash(@Valid @RequestBody CheckoutRequest request) {
        return paymentService.payByCash(request);
    }

    @PostMapping("/webhooks/sepay")
    @Operation(summary = "Thanh toán bằng tiền mặt")
    public void getWebhooks(@Valid @RequestBody CheckoutRequest request) {
//        return paymentService.payByCash(request);
    }
}