package org.hvtra.catalog.client.resilience;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.function.Predicate;

/**
 * Retry {@code retryExceptionPredicate}: true only for failures that a second attempt can plausibly fix.
 *
 * <p>Retried: connect errors, timeouts and HTTP 502/503/504. Never retried: 4xx, HTTP 500 (a bug answers
 * the same way twice), an open circuit breaker and a full bulkhead.
 *
 * <p>Only attach this to idempotent reads. It says nothing about whether repeating a call is safe.
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

            if (current instanceof RetryableException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException) {
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
