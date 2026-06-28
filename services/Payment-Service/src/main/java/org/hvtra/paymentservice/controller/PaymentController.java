package org.hvtra.paymentservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.hvtra.paymentservice.dto.request.CashPaymentRequest;
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

    @PostMapping("/cash")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thanh toán bằng tiền mặt")
    public CashPaymentResponse payByCash(@Valid @RequestBody CashPaymentRequest request) {
        return paymentService.payByCash(request);
    }

//    @PostMapping("/webhooks/sepay")
//    @ResponseStatus(HttpStatus.CREATED)
//    @Operation(summary = "Thanh toán bằng tiền mặt")
//    public CashPaymentResponse payByCash(@Valid @RequestBody CashPaymentRequest request) {
//        return paymentService.payByCash(request);
//    }
}