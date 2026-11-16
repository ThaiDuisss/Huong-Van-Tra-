package org.hvtra.orderservice.client.resilience;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.orderservice.client.resilience.DownstreamUnavailableException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeignFailureClassificationTest {

    private final DownstreamFailurePredicate breakerFailure = new DownstreamFailurePredicate();
    private final TransientReadFailurePredicate retryable = new TransientReadFailurePredicate();

    @ParameterizedTest
    @CsvSource({
            "400, NOT_A_SERVICE_FAILURE, false, false",
            "401, NOT_A_SERVICE_FAILURE, false, false",
            "404, NOT_A_SERVICE_FAILURE, false, false",
            "409, NOT_A_SERVICE_FAILURE, false, false",
            "422, NOT_A_SERVICE_FAILURE, false, false",
            "429, NOT_A_SERVICE_FAILURE, false, false",
            "500, SERVER_ERROR,          true,  false",
            "501, SERVER_ERROR,          true,  false",
            "502, GATEWAY_UNAVAILABLE,   true,  true",
            "503, GATEWAY_UNAVAILABLE,   true,  true",
            "504, GATEWAY_UNAVAILABLE,   true,  true"
    })
    void httpStatusDecidesBreakerFailureAndRetry(
            int status,
            FeignFailureKind kind,
            boolean countsAgainstBreaker,
            boolean retried
    ) {
        Throwable decoded = new FeignClientException(status, "PaymentClient#checkout", "error", null);
        Throwable raw = new FeignException(status, "HTTP " + status) {
        };

        for (Throwable failure : List.of(decoded, raw)) {
            assertThat(FeignFailureKind.of(failure)).isEqualTo(kind);
            assertThat(breakerFailure.test(failure)).isEqualTo(countsAgainstBreaker);
            assertThat(retryable.test(failure)).isEqualTo(retried);
        }
    }

    @Test
    void networkFailuresCountAgainstTheBreakerAndAreRetried() {
        List<Throwable> failures = List.of(
                new ConnectException("refused"),
                new SocketTimeoutException("Read timed out"),
                new UnknownHostException("inventory-service"),
                new NoRouteToHostException("no route"),
                feignIoFailure(new ConnectException("refused")),
                new RuntimeException("outer", new IllegalStateException(new ConnectException("refused")))
        );

        for (Throwable failure : failures) {
            assertThat(FeignFailureKind.of(failure)).isEqualTo(FeignFailureKind.NETWORK);
            assertThat(breakerFailure.test(failure)).isTrue();
            assertThat(retryable.test(failure)).isTrue();
        }
    }

    @Test
    void localRejectionsAreNeitherBreakerFailuresNorRetried() {
        Throwable circuitOpen = CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults("paymentService"));
        Throwable bulkheadFull = BulkheadFullException.createBulkheadFullException(
                Bulkhead.ofDefaults("paymentService"));
        Throwable translated = new DownstreamUnavailableException(
                DownstreamService.PAYMENT, Reason.CIRCUIT_OPEN, "PaymentClient#checkout", circuitOpen);

        for (Throwable failure : List.of(circuitOpen, bulkheadFull, translated)) {
            assertThat(FeignFailureKind.of(failure)).isEqualTo(FeignFailureKind.LOCAL_REJECTION);
            assertThat(breakerFailure.test(failure)).isFalse();
            assertThat(retryable.test(failure)).isFalse();
        }
    }

    @Test
    void aFailureThatUsedUpTheRetryBudgetIsNotRetriedButStillCountsAgainstTheBreaker() {
        Throwable slowTimeout = new RetryBudgetExceededException(
                feignIoFailure(new SocketTimeoutException("Read timed out")));

        assertThat(retryable.test(slowTimeout)).isFalse();
        assertThat(breakerFailure.test(slowTimeout)).isTrue();
    }

    @Test
    void unrelatedOrMissingErrorsAreNotServiceFailures() {
        assertThat(FeignFailureKind.of(new IllegalArgumentException("bad"))).isEqualTo(FeignFailureKind.NOT_A_SERVICE_FAILURE);
        assertThat(FeignFailureKind.of(null)).isEqualTo(FeignFailureKind.NOT_A_SERVICE_FAILURE);
        assertThat(breakerFailure.test(new IllegalArgumentException("bad"))).isFalse();
        assertThat(retryable.test(new IllegalArgumentException("bad"))).isFalse();
        assertThat(breakerFailure.test(null)).isFalse();
        assertThat(retryable.test(null)).isFalse();
    }

    @Test
    void refusedCallIsAFeignClientExceptionWithStatus503() {
        DownstreamUnavailableException exception = new DownstreamUnavailableException(
                DownstreamService.INVENTORY, Reason.BULKHEAD_FULL, "InventoryClient#fulfillSale", null);

        assertThat(exception).isInstanceOf(FeignClientException.class);
        assertThat(exception.getStatus()).isEqualTo(503);
        assertThat(exception.getService()).isEqualTo(DownstreamService.INVENTORY);
        assertThat(exception.getReason()).isEqualTo(Reason.BULKHEAD_FULL);
        assertThat(exception.getMethodKey()).isEqualTo("InventoryClient#fulfillSale");
        assertThat(exception.getMessage()).contains("tồn kho");
    }

    private static RetryableException feignIoFailure(Exception cause) {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://inventory-service/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(-1, cause.getMessage(), Request.HttpMethod.GET, cause, (Long) null, request);
    }
}
