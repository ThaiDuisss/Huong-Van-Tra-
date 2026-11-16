package org.hvtra.iventoryservice.integration.resilience;

import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.spring6.bulkhead.configure.BulkheadAspect;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerAspect;
import io.github.resilience4j.spring6.retry.configure.RetryAspect;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.integration.catalog.client.ResilientCatalogClient;
import org.hvtra.iventoryservice.integration.identity.client.IdentityClient;
import org.hvtra.iventoryservice.integration.identity.client.ResilientIdentityClient;
import org.hvtra.iventoryservice.integration.order.client.OrderReturnClient;
import org.hvtra.iventoryservice.integration.order.client.ResilientOrderReturnClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Drives the real {@code Resilient*Client} beans through the Spring AOP proxy with the Resilience4j
 * configuration from the service's own {@code application.yml}; only the raw Feign proxies are
 * mocked. No database, Kafka, Eureka or network is involved.
 */
@SpringBootTest(
        classes = FeignResilienceTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class FeignResilienceTest {

    private static final String CATALOG = ResilientCatalogClient.INSTANCE;
    private static final String ORDER = ResilientOrderReturnClient.INSTANCE;
    private static final String IDENTITY = ResilientIdentityClient.INSTANCE;

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            AopAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class,
            RetryAutoConfiguration.class,
            BulkheadAutoConfiguration.class
    })
    @Import({ResilientCatalogClient.class, ResilientOrderReturnClient.class, ResilientIdentityClient.class})
    static class TestConfig {

        @Bean(CatalogClient.FEIGN_QUALIFIER)
        CatalogClient rawCatalogClient() {
            return mock(CatalogClient.class);
        }

        @Bean(OrderReturnClient.FEIGN_QUALIFIER)
        OrderReturnClient rawOrderReturnClient() {
            return mock(OrderReturnClient.class);
        }

        @Bean(IdentityClient.FEIGN_QUALIFIER)
        IdentityClient rawIdentityClient() {
            return mock(IdentityClient.class);
        }
    }

    // Unqualified injection, exactly like the application services: must resolve to the decorators.
    @Autowired
    private CatalogClient catalogClient;
    @Autowired
    private OrderReturnClient orderReturnClient;
    @Autowired
    private IdentityClient identityClient;

    @Autowired
    @Qualifier(CatalogClient.FEIGN_QUALIFIER)
    private CatalogClient rawCatalog;
    @Autowired
    @Qualifier(OrderReturnClient.FEIGN_QUALIFIER)
    private OrderReturnClient rawOrder;
    @Autowired
    @Qualifier(IdentityClient.FEIGN_QUALIFIER)
    private IdentityClient rawIdentity;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @Autowired
    private RetryRegistry retryRegistry;
    @Autowired
    private BulkheadRegistry bulkheadRegistry;
    @Autowired
    private RetryAspect retryAspect;
    @Autowired
    private CircuitBreakerAspect circuitBreakerAspect;
    @Autowired
    private BulkheadAspect bulkheadAspect;
    @Autowired
    private Environment environment;

    @BeforeEach
    void resetState() {
        reset(rawCatalog, rawOrder, rawIdentity);
        List.of(CATALOG, ORDER, IDENTITY).forEach(name -> circuitBreakerRegistry.circuitBreaker(name).reset());
    }

    // ---------------------------------------------------------------- wiring / AOP

    @Test
    void unqualifiedInjectionGetsTheProxiedDecorators() {
        assertThat(AopUtils.isAopProxy(catalogClient)).isTrue();
        assertThat(AopUtils.isAopProxy(orderReturnClient)).isTrue();
        assertThat(AopUtils.isAopProxy(identityClient)).isTrue();
        assertThat(AopUtils.getTargetClass(catalogClient)).isEqualTo(ResilientCatalogClient.class);
        assertThat(AopUtils.getTargetClass(orderReturnClient)).isEqualTo(ResilientOrderReturnClient.class);
        assertThat(AopUtils.getTargetClass(identityClient)).isEqualTo(ResilientIdentityClient.class);
    }

    @Test
    void aspectsNestAsRetryAroundCircuitBreakerAroundBulkhead() {
        // Lower order value = higher precedence = outer advice.
        assertThat(retryAspect.getOrder()).isLessThan(circuitBreakerAspect.getOrder());
        assertThat(circuitBreakerAspect.getOrder()).isLessThan(bulkheadAspect.getOrder());
    }

    @Test
    void everyRetryAttemptPassesThroughTheCircuitBreaker() {
        when(rawCatalog.getAllUnits()).thenThrow(readTimeout());

        assertThatThrownBy(() -> catalogClient.getAllUnits()).isInstanceOf(RetryableException.class);

        // One logical call, two attempts, both seen by the breaker => the breaker is inside the retry.
        assertThat(breaker(CATALOG).getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- configuration from application.yml

    @Test
    void breakersAreCountBasedAndSlowCallThresholdIsBelowFeignReadTimeout() {
        Map<String, String> feignNameByInstance = Map.of(
                CATALOG, "catalog-service",
                ORDER, "order-service",
                IDENTITY, "identity-service");

        feignNameByInstance.forEach((instance, feignName) -> {
            CircuitBreakerConfig config = breaker(instance).getCircuitBreakerConfig();
            String prefix = "spring.cloud.openfeign.client.config." + feignName;
            Integer connectTimeout = environment.getProperty(prefix + ".connectTimeout", Integer.class);
            Integer readTimeout = environment.getProperty(prefix + ".readTimeout", Integer.class);

            assertThat(connectTimeout).as("%s connectTimeout", feignName).isNotNull().isPositive();
            assertThat(readTimeout).as("%s readTimeout", feignName).isNotNull().isPositive();
            assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
            assertThat(config.getSlidingWindowSize()).isEqualTo(20);
            assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
            assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
            assertThat(config.getSlowCallDurationThreshold())
                    .as("%s slowCallDurationThreshold", instance)
                    .isLessThan(Duration.ofMillis(readTimeout));

            assertThat(retryRegistry.retry(instance).getRetryConfig().getMaxAttempts()).isEqualTo(2);
            assertThat(bulkheadRegistry.bulkhead(instance).getBulkheadConfig().getMaxWaitDuration()).isZero();
        });
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.default.readTimeout", Integer.class))
                .isNotNull();
        assertThat(environment.getProperty("spring.cloud.openfeign.client.config.file-service.readTimeout", Integer.class))
                .isNotNull();
    }

    // ---------------------------------------------------------------- (a) breaker opens on transient failures

    @Test
    void breakerOpensAfterRepeatedTransientFailuresAndThenFailsFast() {
        when(rawOrder.getReturnById(any())).thenThrow(readTimeout());
        UUID returnId = UUID.randomUUID();

        // minimumNumberOfCalls = 10; each read is attempted twice => 5 logical calls fill the window.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> orderReturnClient.getReturnById(returnId)).isInstanceOf(RetryableException.class);
        }

        assertThat(breaker(ORDER).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(rawOrder, times(10)).getReturnById(returnId);

        // Open breaker: rejected without touching Order-Service, and the rejection is not retried.
        assertThatThrownBy(() -> orderReturnClient.getInventoryDisposition(returnId, UUID.randomUUID()))
                .isInstanceOf(CallNotPermittedException.class);
        verify(rawOrder, times(0)).getInventoryDisposition(any(), any());
        assertThat(breaker(ORDER).getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }

    @Test
    void breakerOpensOnHttp5xxFromWrites() {
        doThrow(downstreamStatus(500)).when(rawCatalog).addIngredientsToSupplier(any(), any());

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> catalogClient.addIngredientsToSupplier(UUID.randomUUID(), List.of(UUID.randomUUID())))
                    .isInstanceOf(FeignClientException.class);
        }

        assertThat(breaker(CATALOG).getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(rawCatalog, times(10)).addIngredientsToSupplier(any(), any());
    }

    // ---------------------------------------------------------------- (b) 4xx never opens the breaker

    @Test
    void clientErrorsDoNotOpenTheBreakerAndAreNotRetried() {
        when(rawOrder.getReturnById(any())).thenThrow(downstreamStatus(404));
        when(rawCatalog.getVariant(any())).thenThrow(downstreamStatus(400));

        for (int i = 0; i < 25; i++) {
            assertThatThrownBy(() -> orderReturnClient.getReturnById(UUID.randomUUID()))
                    .isInstanceOfSatisfying(FeignClientException.class,
                            exception -> assertThat(exception.getStatus()).isEqualTo(404));
            assertThatThrownBy(() -> catalogClient.getVariant(UUID.randomUUID()))
                    .isInstanceOf(FeignClientException.class);
        }

        assertThat(breaker(ORDER).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker(ORDER).getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker(CATALOG).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker(CATALOG).getMetrics().getNumberOfFailedCalls()).isZero();
        verify(rawOrder, times(25)).getReturnById(any());
        verify(rawCatalog, times(25)).getVariant(any());
    }

    // ---------------------------------------------------------------- (c) writes are never retried

    @Test
    void createIngredientIsNotRetriedOnTransientFailure() {
        CatalogClient.CreateIngredientRequest request =
                new CatalogClient.CreateIngredientRequest("Trà xanh", UUID.randomUUID(), null, "ACTIVE");
        when(rawCatalog.createIngredient(any(), any())).thenThrow(readTimeout());

        assertThatThrownBy(() -> catalogClient.createIngredient(null, request)).isInstanceOf(RetryableException.class);

        verify(rawCatalog, times(1)).createIngredient(null, request);
    }

    @Test
    void addIngredientsToSupplierIsNotRetriedOnHttp503() {
        UUID supplierId = UUID.randomUUID();
        List<UUID> ingredientIds = List.of(UUID.randomUUID());
        doThrow(downstreamStatus(503)).when(rawCatalog).addIngredientsToSupplier(supplierId, ingredientIds);

        assertThatThrownBy(() -> catalogClient.addIngredientsToSupplier(supplierId, ingredientIds))
                .isInstanceOf(FeignClientException.class);

        verify(rawCatalog, times(1)).addIngredientsToSupplier(supplierId, ingredientIds);
    }

    // ---------------------------------------------------------------- (d) reads are retried on transient failure

    @Test
    void readIsRetriedOnceAndSucceedsWhenTheFailureWasTransient() {
        UUID unitId = UUID.randomUUID();
        List<CatalogClient.UnitResponse> units =
                List.of(new CatalogClient.UnitResponse(unitId, "Kilogram", "kg", 3, "ACTIVE"));
        when(rawCatalog.getUnitsBatch(List.of(unitId)))
                .thenThrow(downstreamStatus(503))
                .thenReturn(units);

        assertThat(catalogClient.getUnitsBatch(List.of(unitId))).isEqualTo(units);

        verify(rawCatalog, times(2)).getUnitsBatch(List.of(unitId));
    }

    @Test
    void readGivesUpAfterTwoAttemptsAndRethrowsTheOriginalException() {
        FeignClientException gatewayTimeout = downstreamStatus(504);
        when(rawIdentity.getUsersBatch(any())).thenThrow(gatewayTimeout);

        // Same exception instance => existing error translation (BaseGlobalExceptionHandler) is unchanged.
        assertThatThrownBy(() -> identityClient.getUsersBatch(List.of("subject-1"))).isSameAs(gatewayTimeout);

        verify(rawIdentity, times(2)).getUsersBatch(List.of("subject-1"));
    }

    @Test
    void readIsNotRetriedOnHttp500() {
        when(rawCatalog.getBomItems(any())).thenThrow(downstreamStatus(500));
        UUID variantId = UUID.randomUUID();

        assertThatThrownBy(() -> catalogClient.getBomItems(variantId)).isInstanceOf(FeignClientException.class);

        verify(rawCatalog, times(1)).getBomItems(variantId);
        // ... but a 500 is still evidence against the downstream.
        assertThat(breaker(CATALOG).getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- bulkhead

    @Test
    void fullBulkheadRejectsWithoutRetryAndWithoutBeingRecordedByTheBreaker() {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(IDENTITY);
        int permits = bulkhead.getBulkheadConfig().getMaxConcurrentCalls();
        for (int i = 0; i < permits; i++) {
            assertThat(bulkhead.tryAcquirePermission()).isTrue();
        }
        try {
            assertThatThrownBy(() -> identityClient.getUsersBatch(List.of("subject-1")))
                    .isInstanceOf(BulkheadFullException.class);

            verifyNoInteractions(rawIdentity);
            assertThat(breaker(IDENTITY).getMetrics().getNumberOfFailedCalls()).isZero();
            assertThat(breaker(IDENTITY).getMetrics().getNumberOfSuccessfulCalls()).isZero();
            assertThat(breaker(IDENTITY).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        } finally {
            for (int i = 0; i < permits; i++) {
                bulkhead.onComplete();
            }
        }
    }

    @Test
    void instancesAreIsolatedPerDownstreamService() {
        breaker(CATALOG).transitionToOpenState();
        when(rawOrder.getReturnById(any())).thenReturn(null);

        assertThatThrownBy(() -> catalogClient.getAllSuppliers()).isInstanceOf(CallNotPermittedException.class);
        orderReturnClient.getReturnById(UUID.randomUUID());

        verify(rawOrder, times(1)).getReturnById(any());
        assertThat(breaker(ORDER).getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ---------------------------------------------------------------- helpers

    private CircuitBreaker breaker(String name) {
        return circuitBreakerRegistry.circuitBreaker(name);
    }

    private static FeignClientException downstreamStatus(int status) {
        return new FeignClientException(status, "Client#method()", "HTTP " + status, null);
    }

    private static RetryableException readTimeout() {
        Request request = Request.create(Request.HttpMethod.GET, "http://downstream/resource",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return new RetryableException(-1, "Read timed out", Request.HttpMethod.GET,
                new SocketTimeoutException("Read timed out"), (Long) null, request);
    }
}
