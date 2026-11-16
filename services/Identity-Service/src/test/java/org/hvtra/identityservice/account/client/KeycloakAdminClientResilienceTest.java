package org.hvtra.identityservice.account.client;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakersHealthIndicatorAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.hvtra.identityservice.account.config.KeycloakAdminClientConfig;
import org.hvtra.identityservice.account.config.KeycloakAdminProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Slice test: chỉ nạp KeycloakResilience + auto-config Resilience4j với cấu hình THẬT trong
 * application.yml (không DataSource, Flyway, Kafka, Eureka, Security). Keycloak được giả lập bằng
 * MockRestServiceServer: request nào ngoài dự kiến sẽ làm test fail, nên số lần gọi HTTP được kiểm chính xác.
 */
@SpringBootTest(
        classes = KeycloakAdminClientResilienceTest.SliceConfig.class,
        properties = "resilience4j.retry.instances.keycloak.waitDuration=10ms"
)
class KeycloakAdminClientResilienceTest {

    @Configuration(proxyBeanMethods = false)
    @Import({KeycloakResilience.class, KeycloakAdminClientConfig.class})
    @ImportAutoConfiguration({
            RetryAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class,
            BulkheadAutoConfiguration.class,
            CircuitBreakersHealthIndicatorAutoConfiguration.class,
            HealthContributorAutoConfiguration.class,
            HealthEndpointAutoConfiguration.class
    })
    static class SliceConfig {
    }

    private static final String BASE = "http://keycloak.test";
    private static final String TOKEN_URL = BASE + "/realms/hvt/protocol/openid-connect/token";
    private static final String ADMIN = BASE + "/admin/realms/hvt";

    @Autowired
    private KeycloakResilience resilience;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    @Qualifier("keycloakAdminRestClient")
    private RestClient configuredRestClient;

    private CircuitBreaker circuitBreaker;
    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker(KeycloakResilience.KEYCLOAK);
        circuitBreaker.reset();

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        client = new KeycloakAdminClient(
                builder.build(),
                new KeycloakAdminProperties(BASE, "hvt", "identity-admin", "test-secret"),
                Clock.systemUTC(),
                resilience);
    }

    @Test
    void yamlConfigurationIsBoundToTheKeycloakInstance() {
        CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(config.getSlidingWindowSize()).isEqualTo(20);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        // Phải thấp hơn idp.admin-read-timeout (15 s).
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(10));

        assertThat(retryRegistry.retry(KeycloakResilience.KEYCLOAK).getRetryConfig().getMaxAttempts()).isEqualTo(2);
        assertThat(bulkheadRegistry.bulkhead(KeycloakResilience.KEYCLOAK).getBulkheadConfig().getMaxConcurrentCalls())
                .isEqualTo(20);
        assertThat(configuredRestClient).isNotNull();
    }

    @Test
    void healthGroupExposesTheCircuitBreaker() {
        HealthComponent component = healthEndpoint.healthForPath("resilience", "circuitBreakers");

        assertThat(component).isInstanceOfSatisfying(Health.class,
                health -> assertThat(health.getDetails()).containsKey(KeycloakResilience.KEYCLOAK));
    }

    @Test
    void readIsRetriedOnceOnTransientFailure() {
        expectToken();
        server.expect(ExpectedCount.once(), requestTo(ADMIN + "/users/u-1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.once(), requestTo(ADMIN + "/users/u-1"))
                .andRespond(withSuccess("{\"id\":\"u-1\",\"username\":\"lan\"}", MediaType.APPLICATION_JSON));

        assertThat(client.getUser("u-1").getId()).isEqualTo("u-1");

        server.verify();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void readIsRetriedOnTimeoutAndThenReportsKeycloakUnreachable() {
        expectToken();
        server.expect(ExpectedCount.twice(), requestTo(ADMIN + "/roles"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(client::listRealmRoles)
                .isInstanceOfSatisfying(KeycloakAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(KeycloakAdminException.STATUS_UNREACHABLE))
                .isNotInstanceOf(KeycloakUnavailableException.class);

        server.verify();
    }

    @Test
    void readIsNotRetriedOnBusinessError() {
        expectToken();
        server.expect(ExpectedCount.once(), requestTo(ADMIN + "/users/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getUser("missing"))
                .isInstanceOfSatisfying(KeycloakAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(404));

        server.verify();
    }

    @Test
    void tokenEndpointOutageIsReportedAs5xxAndRetriedForReads() {
        server.expect(ExpectedCount.twice(), requestTo(TOKEN_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(client::listRealmRoles)
                .isInstanceOfSatisfying(KeycloakAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(503));

        server.verify();
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    @Test
    void rejectedServiceAccountCredentialsStay401AndAreNeitherRetriedNorRecorded() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(client::listRealmRoles)
                .isInstanceOfSatisfying(KeycloakAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(401));

        server.verify();
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void writesAreNeverRetried() {
        expectToken();
        server.expect(ExpectedCount.once(), requestTo(ADMIN + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.once(), requestTo(ADMIN + "/users/u-1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(ExpectedCount.once(), requestTo(ADMIN + "/users/u-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        server.expect(ExpectedCount.once(), requestTo(ADMIN + "/clients/c-1/roles"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertStatus(() -> client.createUser(new KeycloakUser()), 503);
        assertStatus(() -> client.updateUser("u-1", new KeycloakUser()), KeycloakAdminException.STATUS_UNREACHABLE);
        assertStatus(() -> client.deleteUser("u-1"), 504);
        assertStatus(() -> client.createClientRole("c-1", new KeycloakRole(null, "order:create")), 502);

        // Mỗi lời gọi ghi đúng 1 request dù lỗi thuộc loại "tạm thời".
        server.verify();
    }

    @Test
    void successfulWriteStillWorksThroughTheBreaker() {
        expectToken();
        server.expect(ExpectedCount.once(), requestTo(ADMIN + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withCreatedEntity(URI.create(ADMIN + "/users/u-new")));

        assertThat(client.createUser(new KeycloakUser())).isEqualTo("u-new");
        server.verify();
    }

    @Test
    void breakerOpensAfterRepeatedTransientFailuresAndThenFailsFastWithoutHttpCall() {
        expectToken();
        // minimumNumberOfCalls = 10; mỗi lần đọc = 2 attempt => 5 lần gọi là đủ mở breaker.
        server.expect(ExpectedCount.times(10), requestTo(ADMIN + "/roles"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        for (int i = 0; i < 5; i++) {
            assertStatus(client::listRealmRoles, 503);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Breaker OPEN: đọc lẫn ghi bị từ chối ngay (status 0 => 503 KEYCLOAK_UNAVAILABLE), không retry.
        assertThatThrownBy(client::listRealmRoles)
                .isInstanceOfSatisfying(KeycloakUnavailableException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(KeycloakAdminException.STATUS_UNREACHABLE));
        assertThatThrownBy(() -> client.createUser(new KeycloakUser()))
                .isInstanceOf(KeycloakUnavailableException.class);

        // Đúng 1 request token + 10 request /roles, không có request nào sau khi breaker mở.
        server.verify();
    }

    @Test
    void failedWritesAlsoOpenTheBreaker() {
        expectToken();
        server.expect(ExpectedCount.times(10), requestTo(ADMIN + "/users/u-1/logout"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        for (int i = 0; i < 10; i++) {
            assertStatus(() -> client.logoutUser("u-1"), 500);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        server.verify();
    }

    @Test
    void businessErrorsNeverOpenTheBreaker() {
        expectToken();
        server.expect(ExpectedCount.times(15), requestTo(ADMIN + "/users/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(ExpectedCount.times(15), requestTo(ADMIN + "/users"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(ExpectedCount.times(15), requestTo(ADMIN + "/users/u-1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        for (int i = 0; i < 15; i++) {
            assertStatus(() -> client.getUser("missing"), 404);
            assertStatus(() -> client.createUser(new KeycloakUser()), 409);
            assertStatus(() -> client.deleteUser("u-1"), 403);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        server.verify();
    }

    @Test
    void fullBulkheadRejectsWithoutCallingKeycloakOrOpeningTheBreaker() {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(KeycloakResilience.KEYCLOAK);
        int permits = bulkhead.getBulkheadConfig().getMaxConcurrentCalls();
        for (int i = 0; i < permits; i++) {
            assertThat(bulkhead.tryAcquirePermission()).isTrue();
        }
        try {
            assertThatThrownBy(client::listRealmRoles).isInstanceOf(KeycloakUnavailableException.class);
            assertThatThrownBy(() -> client.deleteUser("u-1")).isInstanceOf(KeycloakUnavailableException.class);
        } finally {
            for (int i = 0; i < permits; i++) {
                bulkhead.onComplete();
            }
        }

        server.verify();
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    private void assertStatus(Runnable call, int expectedStatus) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(KeycloakAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(expectedStatus));
    }

    private void expectToken() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"token-1\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    }
}
