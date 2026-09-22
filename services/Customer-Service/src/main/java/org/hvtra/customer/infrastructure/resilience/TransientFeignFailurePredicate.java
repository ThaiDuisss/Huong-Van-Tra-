package org.hvtra.customer.infrastructure.resilience;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.function.Predicate;

public class TransientFeignFailurePredicate
        implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {

            // Không retry khi Circuit Breaker đã OPEN
            if (current instanceof CallNotPermittedException) {
                return false;
            }

            // Không retry khi Bulkhead đã đầy.
            if (current instanceof BulkheadFullException) {
                return false;
            }

            // Lỗi network / timeout thường có thể hồi phục ngắn hạn
            if (current instanceof RetryableException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException) {
                return true;
            }

            // Chỉ retry HTTP lỗi hạ tầng tạm thời
            if (current instanceof FeignException feignException) {
                int status = feignException.status();

                return status == 502
                        || status == 503
                        || status == 504;
            }

            if (current instanceof FeignClientException feignClientException) {
                int status = feignClientException.getStatus();

                return status == 502
                        || status == 503
                        || status == 504;
            }

            current = current.getCause();
        }

        return false;
    }
}
