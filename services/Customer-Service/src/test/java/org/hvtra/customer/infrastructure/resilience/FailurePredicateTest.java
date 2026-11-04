package org.hvtra.customer.infrastructure.resilience;

import feign.FeignException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class FailurePredicateTest {

    private final OrderFailurePredicate orderFailure = new OrderFailurePredicate();
    private final TransientFeignFailurePredicate transientFailure = new TransientFeignFailurePredicate();

    @Test
    void networkErrorsCountAsFailureAndAreRetried() {
        Throwable connect = new ConnectException("refused");
        Throwable timeout = new SocketTimeoutException("timeout");

        assertThat(orderFailure.test(connect)).isTrue();
        assertThat(orderFailure.test(timeout)).isTrue();
        assertThat(transientFailure.test(connect)).isTrue();
        assertThat(transientFailure.test(timeout)).isTrue();
    }

    @Test
    void networkErrorWrappedInCauseChainIsDetected() {
        Throwable wrapped = new RuntimeException("outer", new IllegalStateException(new ConnectException("refused")));

        assertThat(orderFailure.test(wrapped)).isTrue();
        assertThat(transientFailure.test(wrapped)).isTrue();
    }

    @Test
    void openCircuitAndFullBulkheadAreNeitherFailuresNorRetried() {
        Throwable notPermitted = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("order"));
        Throwable bulkheadFull = BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("order"));

        assertThat(orderFailure.test(notPermitted)).isFalse();
        assertThat(orderFailure.test(bulkheadFull)).isFalse();
        assertThat(transientFailure.test(notPermitted)).isFalse();
        assertThat(transientFailure.test(bulkheadFull)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "400, false, false",
            "404, false, false",
            "500, true,  false",
            "502, true,  true",
            "503, true,  true",
            "504, true,  true"
    })
    void httpStatusDecidesFailureAndRetry(int status, boolean countsAsFailure, boolean retried) {
        Throwable feign = feignException(status);
        Throwable feignClient = new FeignClientException(status, "OrderClient#getOrderById", "error", null);

        assertThat(orderFailure.test(feign)).isEqualTo(countsAsFailure);
        assertThat(orderFailure.test(feignClient)).isEqualTo(countsAsFailure);
        assertThat(transientFailure.test(feign)).isEqualTo(retried);
        assertThat(transientFailure.test(feignClient)).isEqualTo(retried);
    }

    @Test
    void unrelatedOrNullErrorsAreIgnored() {
        assertThat(orderFailure.test(new IllegalArgumentException("bad"))).isFalse();
        assertThat(orderFailure.test(null)).isFalse();
        assertThat(transientFailure.test(new IllegalArgumentException("bad"))).isFalse();
        assertThat(transientFailure.test(null)).isFalse();
    }

    private FeignException feignException(int status) {
        return new FeignException(status, "HTTP " + status) {
        };
    }
}
