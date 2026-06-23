package org.hvtra.paymentservice.client;

import org.hvtra.paymentservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.paymentservice.dto.response.OrderPaymentInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@FeignClient(
        name = "order-service",
        url = "http://localhost:8086/api/orders"
)
public interface OrderClient {

    @GetMapping("/{orderId}/payment-info")
    OrderPaymentInfoResponse getPaymentInfo(@PathVariable UUID orderId);

    @PutMapping("/{orderId}/mark-paid")
    void markOrderPaid(
            @PathVariable UUID orderId,
            @RequestBody MarkOrderPaidRequest request
    );
}