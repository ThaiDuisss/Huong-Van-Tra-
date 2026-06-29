package org.hvtra.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@FeignClient(
        name = "payment-service",
        url = "${clients.payment-service.url}"
)
public interface PaymentTestClient {

    @GetMapping("/internal/payments/test/success")
    Map<String, Object> success();

    @GetMapping("/internal/payments/test/error")
    Map<String, Object> error();

    @GetMapping("/internal/payments/test/slow")
    Map<String, Object> slow();

    @GetMapping("/internal/payments/test/unstable")
    Map<String, Object> unstable();

    @GetMapping("/internal/payments/test/reset")
    Map<String, Object> reset();
}
