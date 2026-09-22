package org.hvtra.customer.infrastructure.resilience;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.function.Predicate;

public class OrderFailurePredicate
        implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {

            // Đây không phải bằng chứng Order bị lỗi.
            if (current instanceof BulkheadFullException
                    || current instanceof CallNotPermittedException) {
                return false;
            }

            // Network / timeout thường cho thấy Order hoặc đường truyền đang có vấn đề.
            if (current instanceof RetryableException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException) {
                return true;
            }

            // Chỉ tính HTTP 5xx là lỗi sức khỏe của Order.
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
