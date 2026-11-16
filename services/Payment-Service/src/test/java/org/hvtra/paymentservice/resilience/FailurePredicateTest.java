package org.hvtra.paymentservice.resilience;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.paymentservice.exception.OrderServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FailurePredicateTest {

    private final OrderFailurePredicate orderFailure = new OrderFailurePredicate();
    private final TransientFeignFailurePredicate transientFailure = new TransientFeignFailurePredicate();

    @Test
    void networkErrorsCountAsFailureAndAreRetried() {
        for (Throwable error : new Throwable[]{
                new ConnectException("refused"),
                new SocketTimeoutException("timeout"),
                new UnknownHostException("order-service")}) {
            assertThat(orderFailure.test(error)).isTrue();
            assertThat(transientFailure.test(error)).isTrue();
        }
    }

    @Test
    void feignRetryableExceptionCountsAsFailureAndIsRetried() {
        Request request = Request.create(Request.HttpMethod.GET, "http://order-service/api/orders/1/payment-info",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Throwable retryable = new RetryableException(-1, "Read timed out", Request.HttpMethod.GET,
                new SocketTimeoutException("Read timed out"), (Long) null, request);

        assertThat(orderFailure.test(retryable)).isTrue();
        assertThat(transientFailure.test(retryable)).isTrue();
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

        for (Throwable rejection : new Throwable[]{
                notPermitted,
                bulkheadFull,
                new OrderServiceUnavailableException("open", notPermitted),
                new OrderServiceUnavailableException("full", bulkheadFull)}) {
            assertThat(orderFailure.test(rejection)).isFalse();
            assertThat(transientFailure.test(rejection)).isFalse();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "400, false, false",
            "401, false, false",
            "403, false, false",
            "404, false, false",
            "409, false, false",
            "422, false, false",
            "429, false, false",
            "500, true,  false",
            "502, true,  true",
            "503, true,  true",
            "504, true,  true"
    })
    void httpStatusDecidesFailureAndRetry(int status, boolean countsAsFailure, boolean retried) {
        Throwable feign = new FeignException(status, "HTTP " + status) {
        };
        Throwable feignClient = new FeignClientException(status, "OrderClient#getPaymentInfo", "error", null);

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
}
