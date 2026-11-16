package org.hvtra.paymentservice.resilience;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Lỗi nào được circuit breaker {@code orderService} ghi nhận là Order-Service không khỏe:
 * lỗi kết nối/IO, timeout và HTTP 5xx. HTTP 4xx là lỗi nghiệp vụ (Order vẫn trả lời bình thường)
 * nên không được làm mở breaker; breaker OPEN / bulkhead đầy cũng không phải bằng chứng Order lỗi.
 */
public class OrderFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof BulkheadFullException
                    || current instanceof CallNotPermittedException) {
                return false;
            }

            if (current instanceof RetryableException
                    || current instanceof IOException) {
                return true;
            }

            if (current instanceof FeignException feignException) {
                return feignException.status() >= 500;
            }

            if (current instanceof FeignClientException feignClientException) {
                return feignClientException.getStatus() >= 500;
            }

            current = current.getCause();
        }

        return false;
    }
}
