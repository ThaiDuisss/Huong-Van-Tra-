package org.hvtra.catalog.client.resilience;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.hvtra.catalog.exception.BadRequestException;
import org.hvtra.catalog.exception.FileServiceUnavailableException;
import org.hvtra.catalog.exception.InventoryServiceUnavailableException;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FailurePredicateTest {

    private final DownstreamFailurePredicate downstreamFailure = new DownstreamFailurePredicate();
    private final TransientFeignFailurePredicate transientFailure = new TransientFeignFailurePredicate();

    @Test
    void networkErrorsCountAsFailureAndAreRetried() {
        Throwable connect = new ConnectException("refused");
        Throwable timeout = new SocketTimeoutException("timeout");

        assertThat(downstreamFailure.test(connect)).isTrue();
        assertThat(downstreamFailure.test(timeout)).isTrue();
        assertThat(transientFailure.test(connect)).isTrue();
        assertThat(transientFailure.test(timeout)).isTrue();
    }

    @Test
    void feignRetryableExceptionCountsAsFailureAndIsRetried() {
        Throwable retryable = retryableException();

        assertThat(downstreamFailure.test(retryable)).isTrue();
        assertThat(transientFailure.test(retryable)).isTrue();
    }

    @Test
    void networkErrorWrappedInCauseChainIsDetected() {
        Throwable wrapped = new RuntimeException("outer", new IllegalStateException(new ConnectException("refused")));

        assertThat(downstreamFailure.test(wrapped)).isTrue();
        assertThat(transientFailure.test(wrapped)).isTrue();
    }

    @Test
    void openCircuitAndFullBulkheadAreNeitherFailuresNorRetried() {
        Throwable notPermitted = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("x"));
        Throwable bulkheadFull = BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("x"));

        assertThat(downstreamFailure.test(notPermitted)).isFalse();
        assertThat(downstreamFailure.test(bulkheadFull)).isFalse();
        assertThat(transientFailure.test(notPermitted)).isFalse();
        assertThat(transientFailure.test(bulkheadFull)).isFalse();
    }

    @Test
    void typedUnavailableExceptionsFromTheFallbacksAreNeitherFailuresNorRetried() {
        Throwable notPermitted = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("x"));
        Throwable inventoryUnavailable = new InventoryServiceUnavailableException("open", notPermitted);
        Throwable fileUnavailable = new FileServiceUnavailableException("open", notPermitted);

        assertThat(downstreamFailure.test(inventoryUnavailable)).isFalse();
        assertThat(downstreamFailure.test(fileUnavailable)).isFalse();
        assertThat(transientFailure.test(inventoryUnavailable)).isFalse();
        assertThat(transientFailure.test(fileUnavailable)).isFalse();
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
        Throwable feign = feignException(status);
        Throwable feignClient = new FeignClientException(status, "FileClient#attachFile", "error", null);

        assertThat(downstreamFailure.test(feign)).isEqualTo(countsAsFailure);
        assertThat(downstreamFailure.test(feignClient)).isEqualTo(countsAsFailure);
        assertThat(transientFailure.test(feign)).isEqualTo(retried);
        assertThat(transientFailure.test(feignClient)).isEqualTo(retried);
    }

    @Test
    void businessUnrelatedOrNullErrorsAreIgnored() {
        assertThat(downstreamFailure.test(new BadRequestException("bad"))).isFalse();
        assertThat(downstreamFailure.test(new IllegalArgumentException("bad"))).isFalse();
        assertThat(downstreamFailure.test(null)).isFalse();
        assertThat(transientFailure.test(new BadRequestException("bad"))).isFalse();
        assertThat(transientFailure.test(new IllegalArgumentException("bad"))).isFalse();
        assertThat(transientFailure.test(null)).isFalse();
    }

    private FeignException feignException(int status) {
        return new FeignException(status, "HTTP " + status) {
        };
    }

    private RetryableException retryableException() {
        Request request = Request.create(
                Request.HttpMethod.PUT,
                "http://file-service/api/file/attach",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        return new RetryableException(
                -1,
                "Read timed out",
                Request.HttpMethod.PUT,
                new SocketTimeoutException("Read timed out"),
                (Long) null,
                request
        );
    }
}
