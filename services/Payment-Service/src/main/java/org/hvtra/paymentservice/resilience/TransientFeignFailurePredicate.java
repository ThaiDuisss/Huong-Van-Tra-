package org.hvtra.paymentservice.resilience;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Quyết định lời gọi ĐỌC sang Order-Service có được thử lại hay không: chỉ lỗi hạ tầng tạm thời
 * (kết nối/IO, timeout, HTTP 502/503/504). Không retry 4xx (kể cả 429 vì không có backoff theo
 * Retry-After), HTTP 500, circuit breaker OPEN hay bulkhead đầy.
 */
public class TransientFeignFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof CallNotPermittedException
                    || current instanceof BulkheadFullException) {
                return false;
            }

            // ConnectException, SocketTimeoutException, UnknownHostException... đều là IOException.
            if (current instanceof RetryableException
                    || current instanceof IOException) {
                return true;
            }

            if (current instanceof FeignException feignException) {
                return isTransientStatus(feignException.status());
            }

            if (current instanceof FeignClientException feignClientException) {
                return isTransientStatus(feignClientException.getStatus());
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean isTransientStatus(int status) {
        return status == 502 || status == 503 || status == 504;
    }
}
