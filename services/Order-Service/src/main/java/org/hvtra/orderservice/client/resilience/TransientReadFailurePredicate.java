package org.hvtra.orderservice.client.resilience;

import java.util.function.Predicate;

/**
 * Retry {@code retryExceptionPredicate}: a read is sent again only after a network failure or an
 * HTTP 502/503/504. A 4xx, a plain 500, an open circuit, a full bulkhead and a failure that already
 * used up the retry budget are never retried.
 */
public class TransientReadFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof RetryBudgetExceededException) {
            return false;
        }
        return switch (FeignFailureKind.of(throwable)) {
            case NETWORK, GATEWAY_UNAVAILABLE -> true;
            case LOCAL_REJECTION, SERVER_ERROR, NOT_A_SERVICE_FAILURE -> false;
        };
    }
}
