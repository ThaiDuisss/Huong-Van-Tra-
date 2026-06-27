package org.hvtra.orderservice.service;

import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.PaymentTestClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTimeLimiterTestService {

    private final PaymentTestClient paymentTestClient;

    @TimeLimiter(name = "paymentService", fallbackMethod = "createPaymentFallback")
    public CompletableFuture<Map<String, Object>> testTimeLimiter() {
        log.info("[Order] Calling payment slow with TimeLimiter");

        return CompletableFuture.supplyAsync(paymentTestClient::slow);
    }

    public CompletableFuture<Map<String, Object>> createPaymentFallback(Throwable ex) {
        log.error("[Order] TimeLimiter fallback called. reason={}", ex.toString());

        return CompletableFuture.completedFuture(Map.of(
                "status", "TIME_LIMITER_FALLBACK",
                "message", "Payment Service phản hồi quá lâu",
                "errorType", ex.getClass().getSimpleName(),
                "error", ex.getMessage()
        ));
    }
}
