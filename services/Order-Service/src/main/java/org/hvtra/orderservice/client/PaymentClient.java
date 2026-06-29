package org.hvtra.orderservice.client;

import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.orderservice.dto.request.PaymentCreateRequest;
import org.hvtra.orderservice.dto.response.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "payment-service1",
        url = "${clients.payment-service.url}"
)
public interface PaymentClient {
    @PostMapping("/api/payments")
    ApiResponse<PaymentResponse> createPayment(@RequestBody PaymentCreateRequest request);
}
