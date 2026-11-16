package org.hvtra.iventoryservice.integration.resilience;

import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import feign.codec.DecodeException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.iventoryservice.shared.exception.BadRequestException;
import org.hvtra.iventoryservice.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

class FeignFailurePredicateTest {

    private final DownstreamFailurePredicate breakerFailure = new DownstreamFailurePredicate();
    private final TransientFeignFailurePredicate retryable = new TransientFeignFailurePredicate();

    @Test
    void transportErrorsCountAsFailureAndAreRetried() {
        Throwable[] transportErrors = {
                new ConnectException("refused"),
                new SocketTimeoutException("Read timed out"),
                new IOException("Connection reset"),
                retryableException(new SocketTimeoutException("Read timed out"))
        };

        for (Throwable error : transportErrors) {
            assertThat(breakerFailure.test(error)).as("breaker failure: %s", error).isTrue();
            assertThat(retryable.test(error)).as("retried: %s", error).isTrue();
        }
    }

    @Test
    void transportErrorWrappedInCauseChainIsDetected() {
        Throwable wrapped = new CompletionException(new UncheckedIOException(new ConnectException("refused")));

        assertThat(breakerFailure.test(wrapped)).isTrue();
        assertThat(retryable.test(wrapped)).isTrue();
    }

    @Test
    void openCircuitAndFullBulkheadAreNeitherFailuresNorRetried() {
        Throwable notPermitted = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("catalogService"));
        Throwable bulkheadFull = BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("catalogService"));

        assertThat(breakerFailure.test(notPermitted)).isFalse();
        assertThat(breakerFailure.test(bulkheadFull)).isFalse();
        assertThat(retryable.test(notPermitted)).isFalse();
        assertThat(retryable.test(bulkheadFull)).isFalse();
        // Also when something upstream wrapped them.
        assertThat(retryable.test(new CompletionException(notPermitted))).isFalse();
        assertThat(breakerFailure.test(new CompletionException(bulkheadFull))).isFalse();
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
            "501, true,  false",
            "502, true,  true",
            "503, true,  true",
            "504, true,  true"
    })
    void httpStatusDecidesFailureAndRetry(int status, boolean countsAsFailure, boolean retried) {
        // What the application actually sees: CommonFeignErrorDecoder -> FeignClientException.
        Throwable decoded = new FeignClientException(status, "CatalogClient#getVariant(UUID)", "error", null);
        // Defensive: a raw FeignException that bypassed the decoder.
        Throwable raw = new FeignException(status, "HTTP " + status) {
        };

        assertThat(breakerFailure.test(decoded)).isEqualTo(countsAsFailure);
        assertThat(breakerFailure.test(raw)).isEqualTo(countsAsFailure);
        assertThat(retryable.test(decoded)).isEqualTo(retried);
        assertThat(retryable.test(raw)).isEqualTo(retried);
    }

    @Test
    void undecodableSuccessResponseIsNotATransportFailure() {
        // DecodeException carries the 2xx status and an IOException-derived cause; the response did
        // arrive, so repeating the call or blaming the downstream's availability would be wrong.
        Throwable decode = new DecodeException(200, "bad json", request(), new IOException("Unexpected token"));

        assertThat(breakerFailure.test(decode)).isFalse();
        assertThat(retryable.test(decode)).isFalse();
    }

    @Test
    void businessAndUnrelatedErrorsAreIgnored() {
        Throwable[] notDownstreamFaults = {
                new BadRequestException("Only RECEIVED order returns can be QC'd"),
                new ResourceNotFoundException("Order return", UUID.randomUUID()),
                new IllegalStateException("Order inventory command authorization is unavailable"),
                new IllegalArgumentException("bad")
        };

        for (Throwable error : notDownstreamFaults) {
            assertThat(breakerFailure.test(error)).as("breaker failure: %s", error).isFalse();
            assertThat(retryable.test(error)).as("retried: %s", error).isFalse();
        }
        assertThat(breakerFailure.test(null)).isFalse();
        assertThat(retryable.test(null)).isFalse();
    }

    @Test
    void cyclicCauseChainTerminates() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        assertThat(breakerFailure.test(first)).isFalse();
        assertThat(retryable.test(first)).isFalse();
    }

    private static RetryableException retryableException(Throwable cause) {
        return new RetryableException(-1, cause.getMessage(), Request.HttpMethod.GET, cause, (Long) null, request());
    }

    private static Request request() {
        return Request.create(Request.HttpMethod.GET, "http://catalog-service/internal/catalog/units",
                Map.of(), null, StandardCharsets.UTF_8, null);
    }
}
