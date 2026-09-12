package org.hvtra.orderservice.client;

import org.hvtra.orderservice.dto.request.PaymentCreateRequest;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@FeignClient(name = "payment-service", url = "http://localhost:8087")
public interface PaymentClient {

    @GetMapping("/api/payments/orders/{orderId}")
    PaymentOrderInfoResponse getPaymentByOrderId(@PathVariable("orderId") UUID orderId);

    @PostMapping("/api/payments/checkout")
    PaymentResponse checkout(@RequestBody PaymentCreateRequest request);

    @PostMapping("/api/payments/orders/{orderId}/refunds")
    PaymentRefundResponse refundOrderReturn(
            @PathVariable("orderId") UUID orderId,
            @RequestBody PaymentRefundRequest request
    );

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

    record PaymentRefundRequest(
            UUID returnId,
            BigDecimal amount
    ) {}

    record PaymentRefundResponse(
            UUID paymentId,
            UUID orderId,
            UUID refundTransactionId,
            UUID returnId,
            BigDecimal refundAmount,
            BigDecimal totalAmountRefunded,
            String paymentStatus,
            String method,
            LocalDateTime refundedAt
    ) {}
}
