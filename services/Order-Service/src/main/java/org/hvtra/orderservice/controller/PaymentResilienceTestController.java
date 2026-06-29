package org.hvtra.orderservice.controller;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.service.PaymentResilienceTestService;
import org.hvtra.orderservice.service.PaymentTimeLimiterTestService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/orders/resilience-test")
@RequiredArgsConstructor
public class PaymentResilienceTestController {

    private final PaymentResilienceTestService paymentResilienceTestService;
    private final PaymentTimeLimiterTestService paymentTimeLimiterTestService;

    @GetMapping("/success")
    public Map<String, Object> success() {
        return paymentResilienceTestService.testSuccess();
    }

    @GetMapping("/error")
    public Map<String, Object> error() {
        return paymentResilienceTestService.testError();
    }

    @GetMapping("/slow")
    public Map<String, Object> slow() {
        return paymentResilienceTestService.testSlow();
    }

    @GetMapping("/unstable")
    public Map<String, Object> unstable() {
        return paymentResilienceTestService.testUnstable();
    }

    @GetMapping("/reset")
    public Map<String, Object> reset() {
        return paymentResilienceTestService.resetUnstableCounter();
    }

    @GetMapping("/timelimiter")
    public CompletableFuture<Map<String, Object>> timeLimiter() {
        return paymentTimeLimiterTestService.testTimeLimiter();
    }
}
