package org.hvtra.orderservice.client.resilience;

import java.util.function.Predicate;

/**
 * Circuit breaker {@code recordFailurePredicate}: only network failures and HTTP 5xx count against
 * the downstream service. A 4xx or business rejection means the service answered, so it is recorded
 * as a success and never opens the breaker.
 */
public class DownstreamFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return switch (FeignFailureKind.of(throwable)) {
            case NETWORK, GATEWAY_UNAVAILABLE, SERVER_ERROR -> true;
            case LOCAL_REJECTION, NOT_A_SERVICE_FAILURE -> false;
        };
    }
}
