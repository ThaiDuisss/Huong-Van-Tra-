package org.hvtra.iventoryservice.integration.resilience;

import com.sun.net.httpserver.HttpServer;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.hvtra.commonservice.common_core.config.FeignConfig;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.integration.catalog.client.ResilientCatalogClient;
import org.hvtra.iventoryservice.integration.identity.client.IdentityClient;
import org.hvtra.iventoryservice.integration.identity.client.ResilientIdentityClient;
import org.hvtra.iventoryservice.integration.order.client.OrderReturnClient;
import org.hvtra.iventoryservice.integration.order.client.ResilientOrderReturnClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end wiring check with the real Feign proxies (same {@code @FeignClient} definitions, same
 * Common {@code FeignConfig} / error decoder, same {@code application.yml}) pointed at an in-process
 * HTTP server bound to the loopback interface. Proves that the decorators are what gets injected in
 * a context that also contains the Feign beans, and that real HTTP statuses / timeouts are
 * classified the way the mocked tests assume.
 */
@SpringBootTest(
        classes = FeignResilienceWiringTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        // application.yml reads this from the environment; the stub does not check it.
        properties = "security.internal.header-secret=test-only-header-secret"
)
class FeignResilienceWiringTest {

    private static final StubServer STUB = new StubServer();

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(
            clients = {CatalogClient.class, OrderReturnClient.class, IdentityClient.class},
            defaultConfiguration = FeignConfig.class
    )
    @ImportAutoConfiguration({
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            FeignAutoConfiguration.class,
            AopAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class,
            RetryAutoConfiguration.class,
            BulkheadAutoConfiguration.class
    })
    @Import({ResilientCatalogClient.class, ResilientOrderReturnClient.class, ResilientIdentityClient.class})
    static class TestConfig {
    }

    @DynamicPropertySource
    static void feignTargets(DynamicPropertyRegistry registry) {
        String prefix = "spring.cloud.openfeign.client.config.";
        // A fixed URL replaces service discovery; nothing leaves the loopback interface.
        registry.add(prefix + "catalog-service.url", STUB::baseUrl);
        registry.add(prefix + "order-service.url", STUB::baseUrl);
        registry.add(prefix + "identity-service.url", STUB::baseUrl);
        // Shorter than the production value only to keep the timeout test fast.
        registry.add(prefix + "identity-service.readTimeout", () -> "300");
    }

    @AfterAll
    static void stopServer() {
        STUB.stop();
    }

    @Autowired
    private CatalogClient catalogClient;
    @Autowired
    private OrderReturnClient orderReturnClient;
    @Autowired
    private IdentityClient identityClient;
    @Autowired
    @Qualifier(CatalogClient.FEIGN_QUALIFIER)
    private CatalogClient rawCatalogClient;
    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetState() {
        STUB.reset();
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    void decoratorsWinUnqualifiedInjectionNextToTheRealFeignBeans() {
        assertThat(AopUtils.getTargetClass(catalogClient)).isEqualTo(ResilientCatalogClient.class);
        assertThat(AopUtils.getTargetClass(orderReturnClient)).isEqualTo(ResilientOrderReturnClient.class);
        assertThat(AopUtils.getTargetClass(identityClient)).isEqualTo(ResilientIdentityClient.class);
        assertThat(rawCatalogClient).isNotInstanceOf(ResilientCatalogClient.class);
    }

    @Test
    void happyPathIsUnchanged() {
        STUB.respond("GET /internal/catalog/units", 200,
                "[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"name\":\"Kilogram\",\"symbol\":\"kg\"}]", 0);

        List<CatalogClient.UnitResponse> units = catalogClient.getAllUnits();

        assertThat(units).extracting(CatalogClient.UnitResponse::symbol).containsExactly("kg");
        assertThat(STUB.hits("GET /internal/catalog/units")).isEqualTo(1);
    }

    @Test
    void readIsRetriedOnHttp503AndSurfacesTheDecodedException() {
        STUB.respond("GET /internal/catalog/units", 503, "{\"message\":\"down for maintenance\"}", 0);

        assertThatThrownBy(() -> catalogClient.getAllUnits())
                .isInstanceOfSatisfying(FeignClientException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(503);
                    assertThat(exception.getMessage()).isEqualTo("down for maintenance");
                });

        assertThat(STUB.hits("GET /internal/catalog/units")).isEqualTo(2);
        assertThat(circuitBreakerRegistry.circuitBreaker(ResilientCatalogClient.INSTANCE).getMetrics().getNumberOfFailedCalls())
                .isEqualTo(2);
    }

    @Test
    void notFoundIsNeitherRetriedNorCountedAgainstTheBreaker() {
        UUID returnId = UUID.randomUUID();
        String route = "GET /api/orders/returns/" + returnId;
        STUB.respond(route, 404, "{\"message\":\"Order return not found\"}", 0);

        assertThatThrownBy(() -> orderReturnClient.getReturnById(returnId))
                .isInstanceOfSatisfying(FeignClientException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(404));

        assertThat(STUB.hits(route)).isEqualTo(1);
        assertThat(circuitBreakerRegistry.circuitBreaker(ResilientOrderReturnClient.INSTANCE).getMetrics().getNumberOfFailedCalls())
                .isZero();
    }

    @Test
    void writeIsSentExactlyOnceEvenWhenTheResponseIsATransientError() {
        STUB.respond("POST /internal/catalog/ingredients", 503, "{\"message\":\"unavailable\"}", 0);

        assertThatThrownBy(() -> catalogClient.createIngredient(
                null, new CatalogClient.CreateIngredientRequest("Trà xanh", UUID.randomUUID(), null, "ACTIVE")))
                .isInstanceOf(FeignClientException.class);

        assertThat(STUB.hits("POST /internal/catalog/ingredients")).isEqualTo(1);
    }

    @Test
    void feignReadTimeoutIsAppliedPerClientAndRetriedForReads() {
        // Stub answers after 1.5 s; the identity-service readTimeout is 300 ms in this test.
        STUB.respond("POST /internal/identity/users/batch", 200, "[]", 1500);

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> identityClient.getUsersBatch(List.of("subject-1")))
                .isInstanceOf(RetryableException.class);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertThat(STUB.hits("POST /internal/identity/users/batch")).isEqualTo(2);
        // 2 attempts x 300 ms + 200 ms backoff, far below one un-timed-out 1.5 s response per attempt.
        assertThat(elapsedMillis).isLessThan(2500);
    }

    /** Minimal loopback HTTP stub: fixed response per "METHOD path", counts requests. */
    private static final class StubServer {

        private record Stub(int status, String body, long delayMillis) {
        }

        private final Map<String, Stub> stubs = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        private final HttpServer server;

        StubServer() {
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot start loopback stub server", exception);
            }
            server.createContext("/", exchange -> {
                String route = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
                hits.computeIfAbsent(route, key -> new AtomicInteger()).incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                Stub stub = stubs.getOrDefault(route, new Stub(404, "{\"message\":\"no stub\"}", 0));
                try {
                    if (stub.delayMillis() > 0) {
                        Thread.sleep(stub.delayMillis());
                    }
                    byte[] body = stub.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(stub.status(), body.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // The client gave up (read timeout) before the delayed response was written.
                } finally {
                    exchange.close();
                }
            });
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "feign-stub-server");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void respond(String route, int status, String body, long delayMillis) {
            stubs.put(route, new Stub(status, body, delayMillis));
        }

        int hits(String route) {
            AtomicInteger counter = hits.get(route);
            return counter == null ? 0 : counter.get();
        }

        void reset() {
            stubs.clear();
            hits.clear();
        }

        void stop() {
            server.stop(0);
        }
    }
}
