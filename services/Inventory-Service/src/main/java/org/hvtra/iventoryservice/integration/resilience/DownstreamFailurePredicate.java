package org.hvtra.iventoryservice.integration.resilience;

import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * Circuit breaker {@code recordFailurePredicate}: decides whether an exception is evidence that the
 * downstream service (or the network path to it) is unhealthy.
 *
 * <p>Only transport failures and HTTP 5xx count. A 4xx or a local business exception means the
 * downstream answered correctly, so it must never open the breaker.
 */
public class DownstreamFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable current = throwable;
        // Bounded walk: a cause chain is never this deep, the limit only guards against cycles.
        for (int depth = 0; current != null && depth < 20; depth++) {
            // Rejections produced by our own protection say nothing about the downstream.
            if (current instanceof CallNotPermittedException || current instanceof BulkheadFullException) {
                return false;
            }
            // Connect / read timeout / connection reset. Feign wraps them in RetryableException.
            if (current instanceof RetryableException || current instanceof IOException) {
                return true;
            }
            // CommonFeignErrorDecoder turns every non-2xx response into FeignClientException.
            if (current instanceof FeignClientException feignClientException) {
                return feignClientException.getStatus() >= 500;
            }
            if (current instanceof FeignException feignException) {
                return feignException.status() >= 500;
            }
            current = current.getCause();
        }
        return false;
    }
}
