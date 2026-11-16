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
 * Circuit breaker {@code recordFailurePredicate}: decides whether an exception is evidence that the
 * downstream service (or the network path to it) is unhealthy.
 *
 * <p>Counted: connect errors, timeouts and HTTP 5xx. Not counted: 4xx and business errors (the downstream
 * answered correctly, the request was wrong) and Resilience4j's own rejections.
 */
public class DownstreamFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            // Our own rejections say nothing about the health of the downstream service.
            if (current instanceof CallNotPermittedException
                    || current instanceof BulkheadFullException) {
                return false;
            }

            // Feign wraps connect/read timeouts and other I/O errors in RetryableException.
            if (current instanceof RetryableException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException) {
                return true;
            }

            if (current instanceof FeignException feignException) {
                return feignException.status() >= 500;
            }

            // Produced by CommonFeignErrorDecoder for every non-2xx response.
            if (current instanceof FeignClientException feignClientException) {
                return feignClientException.getStatus() >= 500;
            }

            current = current.getCause();
        }

        return false;
    }
}
