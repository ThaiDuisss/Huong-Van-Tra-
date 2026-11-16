package org.hvtra.identityservice.account.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.hvtra.identityservice.account.client.KeycloakAdminException;
import org.hvtra.identityservice.account.client.KeycloakUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakFailurePredicateTest {

    private final KeycloakFailurePredicate keycloakFailure = new KeycloakFailurePredicate();
    private final KeycloakTransientFailurePredicate transientFailure = new KeycloakTransientFailurePredicate();

    @ParameterizedTest
    @CsvSource({
            "400, false, false",
            "401, false, false",
            "403, false, false",
            "404, false, false",
            "409, false, false",
            "429, false, false",
            "500, true,  false",
            "502, true,  true",
            "503, true,  true",
            "504, true,  true"
    })
    void keycloakHttpStatusDecidesFailureAndRetry(int status, boolean countsAsFailure, boolean retried) {
        Throwable error = new KeycloakAdminException(status, "HTTP " + status);

        assertThat(keycloakFailure.test(error)).isEqualTo(countsAsFailure);
        assertThat(transientFailure.test(error)).isEqualTo(retried);
    }

    @Test
    void unreachableKeycloakCountsAsFailureAndIsRetried() {
        Throwable unreachable = new KeycloakAdminException(KeycloakAdminException.STATUS_UNREACHABLE,
                "Không kết nối được Keycloak", new ResourceAccessException("I/O error", new ConnectException("refused")));

        assertThat(keycloakFailure.test(unreachable)).isTrue();
        assertThat(transientFailure.test(unreachable)).isTrue();
    }

    @Test
    void missingConfigurationIsALocalProblem() {
        Throwable notConfigured = new KeycloakAdminException(KeycloakAdminException.STATUS_NOT_CONFIGURED, "chưa cấu hình");

        assertThat(keycloakFailure.test(notConfigured)).isFalse();
        assertThat(transientFailure.test(notConfigured)).isFalse();
    }

    @Test
    void openCircuitAndFullBulkheadAreNeitherFailuresNorRetried() {
        Throwable notPermitted = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("keycloak"));
        Throwable bulkheadFull = BulkheadFullException.createBulkheadFullException(Bulkhead.ofDefaults("keycloak"));

        for (Throwable rejection : new Throwable[]{
                notPermitted,
                bulkheadFull,
                // Mang status 0 để được dịch thành 503, nhưng không phải bằng chứng Keycloak lỗi.
                new KeycloakUnavailableException("open", notPermitted),
                new KeycloakUnavailableException("full", bulkheadFull)}) {
            assertThat(keycloakFailure.test(rejection)).isFalse();
            assertThat(transientFailure.test(rejection)).isFalse();
        }
    }

    @Test
    void untranslatedRestClientErrorsAreClassifiedTheSameWay() {
        Throwable timeout = new ResourceAccessException("I/O error", new SocketTimeoutException("timeout"));
        Throwable wrappedIo = new IllegalStateException(new ConnectException("refused"));
        Throwable unavailable = new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
        Throwable internal = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        Throwable conflict = new HttpClientErrorException(HttpStatus.CONFLICT);

        assertThat(keycloakFailure.test(timeout)).isTrue();
        assertThat(transientFailure.test(timeout)).isTrue();
        assertThat(keycloakFailure.test(wrappedIo)).isTrue();
        assertThat(transientFailure.test(wrappedIo)).isTrue();
        assertThat(keycloakFailure.test(unavailable)).isTrue();
        assertThat(transientFailure.test(unavailable)).isTrue();
        assertThat(keycloakFailure.test(internal)).isTrue();
        assertThat(transientFailure.test(internal)).isFalse();
        assertThat(keycloakFailure.test(conflict)).isFalse();
        assertThat(transientFailure.test(conflict)).isFalse();
    }

    @Test
    void unrelatedOrNullErrorsAreIgnored() {
        assertThat(keycloakFailure.test(new IllegalArgumentException("bad"))).isFalse();
        assertThat(keycloakFailure.test(null)).isFalse();
        assertThat(transientFailure.test(new IllegalArgumentException("bad"))).isFalse();
        assertThat(transientFailure.test(null)).isFalse();
    }
}
