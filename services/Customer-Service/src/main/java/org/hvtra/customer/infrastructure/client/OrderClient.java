package org.hvtra.customer.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@FeignClient(
        name = "order-service",
        path = "/api/orders"
)
public interface OrderClient {

    @GetMapping("/{orderId}")
    OrderResponse getOrderById(@PathVariable("orderId") UUID orderId);

    record OrderResponse(
            UUID id,
            String orderCode,
            UUID customerId,
            BigDecimal originalAmount,
            BigDecimal finalAmount,
            LocalDateTime soldAt
    ) {
    }
}
