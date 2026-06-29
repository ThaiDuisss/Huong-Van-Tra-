package org.hvtra.orderservice.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.PaymentTestClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentResilienceTestService {

    private final PaymentTestClient paymentTestClient;

    @CircuitBreaker(name = "paymentService", fallbackMethod = "")
    public Map<String, Object> testSuccess() {
        log.info("[Order] Calling payment success");
        return paymentTestClient.success();
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "createPaymentFallback")
    public Map<String, Object> testError() {
        log.info("[Order] Calling payment error");
        return paymentTestClient.error();
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "createPaymentFallback")
    public Map<String, Object> testSlow() {
        log.info("[Order] Calling payment slow");
        return paymentTestClient.slow();
    }

    @Retry(name = "paymentService", fallbackMethod = "createPaymentFallback")
    public Map<String, Object> testUnstable() {
        log.info("[Order] Calling payment unstable");
        return paymentTestClient.unstable();
    }

    public Map<String, Object> resetUnstableCounter() {
        log.info("[Order] Resetting payment unstable counter");
        return paymentTestClient.reset();
    }

    public Map<String, Object> createPaymentFallback(Throwable ex) {
        log.error("[Order] Fallback called. reason={}", ex.toString());

        return Map.of(
                "status", "FALLBACK",
                "message", "Payment Service tạm thời không phản hồi",
                "errorType", ex.getClass().getSimpleName(),
                "error", ex.getMessage()
        );
    }
}
