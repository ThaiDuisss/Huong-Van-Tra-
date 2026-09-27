package org.hvtra.iventoryservice.shared.async;

import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Chạy việc (thường là Feign call) trên thread khác nhưng vẫn giữ request context — header auth được
 * FeignConfig forward từ request hiện tại — và MDC (request id cho log).
 * Thread gọi phải join() trước khi request kết thúc.
 */
public final class RequestContextAsync {

    private RequestContextAsync() {
    }

    public static <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return CompletableFuture.supplyAsync(() -> {
            RequestContextHolder.setRequestAttributes(attributes);
            if (mdc != null) MDC.setContextMap(mdc);
            try {
                return supplier.get();
            } finally {
                RequestContextHolder.resetRequestAttributes();
                MDC.clear();
            }
        });
    }
}
