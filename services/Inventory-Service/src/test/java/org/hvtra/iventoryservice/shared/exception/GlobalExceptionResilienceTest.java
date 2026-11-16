package org.hvtra.iventoryservice.shared.exception;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.hvtra.commonservice.common_core.exception.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionResilienceTest {

    private final GlobalException handler = new GlobalException();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/inventory/balances");

    @Test
    void openCircuitBreakerIsAnsweredWith503() {
        CallNotPermittedException exception =
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("catalogService"));

        ResponseEntity<ErrorResponse> response = handler.handleCircuitOpen(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("15");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DOWNSTREAM_SERVICE_UNAVAILABLE");
        assertThat(response.getBody().status()).isEqualTo(503);
    }

    @Test
    void fullBulkheadIsAnsweredWith503() {
        BulkheadFullException exception =
                BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("catalogService"));

        ResponseEntity<ErrorResponse> response = handler.handleBulkheadFull(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DOWNSTREAM_SERVICE_BUSY");
        assertThat(response.getBody().status()).isEqualTo(503);
    }
}
