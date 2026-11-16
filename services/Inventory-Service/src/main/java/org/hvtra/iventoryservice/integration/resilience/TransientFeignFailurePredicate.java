package org.hvtra.iventoryservice.integration.resilience;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Retry {@code retryExceptionPredicate}: true only for failures that a second attempt can
 * plausibly fix — transport errors and the gateway-style statuses 502/503/504.
 *
 * <p>Never retried: any 4xx, HTTP 500 (the downstream ran the request and failed), an open circuit
 * breaker and a full bulkhead. Only idempotent reads carry {@code @Retry} at all.
 */
public class TransientFeignFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable current = throwable;
        // Bounded walk: a cause chain is never this deep, the limit only guards against cycles.
        for (int depth = 0; current != null && depth < 20; depth++) {
            if (current instanceof CallNotPermittedException || current instanceof BulkheadFullException) {
                return false;
            }
            if (current instanceof RetryableException || current instanceof IOException) {
                return true;
            }
            if (current instanceof FeignClientException feignClientException) {
                return isTransientStatus(feignClientException.getStatus());
            }
            if (current instanceof FeignException feignException) {
                return isTransientStatus(feignException.status());
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isTransientStatus(int status) {
        return status == 502 || status == 503 || status == 504;
    }
}
