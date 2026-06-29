package org.hvtra.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "payment-service", url = "http://localhost:8087")
public interface PaymentClient {

    @GetMapping("/api/payments/orders/{orderId}")
    PaymentOrderInfoResponse getPaymentByOrderId(@PathVariable("orderId") UUID orderId);

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
