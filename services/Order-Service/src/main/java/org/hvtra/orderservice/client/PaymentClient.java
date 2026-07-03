package org.hvtra.orderservice.client;

import org.hvtra.orderservice.dto.request.PaymentCreateRequest;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "payment-service", url = "http://localhost:8087")
public interface PaymentClient {

    @GetMapping("/api/payments/orders/{orderId}")
    PaymentOrderInfoResponse getPaymentByOrderId(@PathVariable("orderId") UUID orderId);

    @PostMapping("/api/payments/checkout")
    PaymentResponse getPaymentByOrderId(@RequestBody PaymentCreateRequest request);

    record PaymentOrderInfoResponse(
            UUID paymentId,
            String paymentCode,
            UUID orderId,
            BigDecimal amountRequired,
            BigDecimal amountPaid,
            BigDecimal receivedAmount,
            BigDecimal changeAmount,
            String status,
            String method,
            String methodLabel
    ) {}
}
