package org.hvtra.catalog.client.gateway;

import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.exception.FileServiceUnavailableException;
import org.hvtra.catalog.exception.InventoryServiceUnavailableException;
import org.hvtra.commonservice.common_core.client.FileClient;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the real Resilience4j aspects with the real {@code resilience4j} block of the main
 * application.yml (the test application.yml shadows it on the classpath, so it is read from disk)
 * around mocked Feign clients. No database, Kafka, Eureka or HTTP call is involved.
 */
class FeignResilienceTest {

    private static final int MINIMUM_NUMBER_OF_CALLS = 10;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(FeignResilienceTest::addMainApplicationYaml)
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class,
                    RetryAutoConfiguration.class,
                    BulkheadAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class);

    private final InventoryClient.StockBatchRequest stockRequest =
            new InventoryClient.StockBatchRequest(List.of(UUID.randomUUID()));
    private final FileClient.FileAttach fileAttach = FileClient.FileAttach.builder()
            .fieldId(UUID.randomUUID())
            .refId(UUID.randomUUID())
            .build();

    // ---------------------------------------------------------------- wiring / configuration

    @Test
    void everyFileClientInjectionPointReceivesTheProtectedDecorator() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FileClient.class)).isInstanceOf(ResilientFileClient.class);
        });
    }

    @Test
    void instancesAreConfiguredFromApplicationYaml() {
        contextRunner.run(context -> {
            for (String name : List.of(InventoryGateway.INVENTORY_SERVICE, ResilientFileClient.FILE_SERVICE)) {
                CircuitBreakerConfig config = circuitBreaker(context, name).getCircuitBreakerConfig();
                assertThat(config.getSlidingWindowType())
                        .isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
                assertThat(config.getSlidingWindowSize()).isEqualTo(20);
                assertThat(config.getMinimumNumberOfCalls()).isEqualTo(MINIMUM_NUMBER_OF_CALLS);
                assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
                assertThat(config.getSlowCallRateThreshold()).isEqualTo(80f);
                assertThat(bulkhead(context, name).getBulkheadConfig().getMaxWaitDuration())
                        .isEqualTo(Duration.ZERO);
            }
            assertThat(circuitBreaker(context, InventoryGateway.INVENTORY_SERVICE)
                    .getCircuitBreakerConfig().getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(2));
            assertThat(circuitBreaker(context, ResilientFileClient.FILE_SERVICE)
                    .getCircuitBreakerConfig().getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(3));
            assertThat(context.getBean(RetryRegistry.class).retry(InventoryGateway.INVENTORY_SERVICE)
                    .getRetryConfig().getMaxAttempts()).isEqualTo(2);
        });
    }

    // ---------------------------------------------------------------- happy path

    @Test
    void successfulCallsAreDelegatedOnce() {
        contextRunner.run(context -> {
            List<InventoryClient.StockBatchResponse> stocks = List.of(
                    new InventoryClient.StockBatchResponse(UUID.randomUUID(), null, null, "IN_STOCK"));
            when(inventoryClient(context).getStocksByProductIds(stockRequest)).thenReturn(stocks);

            assertThat(context.getBean(InventoryGateway.class).getStocksByProductIds(stockRequest)).isSameAs(stocks);
            context.getBean(FileClient.class).attachFile(fileAttach);

            verify(inventoryClient(context), times(1)).getStocksByProductIds(stockRequest);
            verify(feignFileClient(context), times(1)).attachFile(fileAttach);
        });
    }

    // ---------------------------------------------------------------- retry

    @Test
    void readIsRetriedOnTransientHttpFailure() {
        contextRunner.run(context -> {
            List<InventoryClient.StockBatchResponse> stocks = List.of();
            when(inventoryClient(context).getStocksByProductIds(stockRequest))
                    .thenThrow(httpError(503))
                    .thenReturn(stocks);

            assertThat(context.getBean(InventoryGateway.class).getStocksByProductIds(stockRequest)).isSameAs(stocks);

            verify(inventoryClient(context), times(2)).getStocksByProductIds(stockRequest);
        });
    }

    @Test
    void readIsRetriedOnTimeoutAndEveryAttemptIsRecordedByTheBreaker() {
        contextRunner.run(context -> {
            InventoryClient.StockVariantBatchRequest request =
                    new InventoryClient.StockVariantBatchRequest(List.of(UUID.randomUUID()));
            RetryableException timeout = timeout();
            when(inventoryClient(context).getStocksByVariantIds(request)).thenThrow(timeout);

            // After the last attempt the original exception surfaces unchanged (-> 503 in the handler).
            assertThatThrownBy(() -> context.getBean(InventoryGateway.class).getStocksByVariantIds(request))
                    .isSameAs(timeout);

            verify(inventoryClient(context), times(2)).getStocksByVariantIds(request);
            // Retry is the outer aspect: both attempts went through the circuit breaker.
            assertThat(circuitBreaker(context, InventoryGateway.INVENTORY_SERVICE).getMetrics()
                    .getNumberOfFailedCalls()).isEqualTo(2);
        });
    }

    @Test
    void readIsNotRetriedOnClientErrorOrHttp500() {
        contextRunner.run(context -> {
            FeignClientException badRequest = httpError(400);
            FeignClientException serverError = httpError(500);
            when(inventoryClient(context).getStocksByProductIds(stockRequest))
                    .thenThrow(badRequest)
                    .thenThrow(serverError);
            InventoryGateway gateway = context.getBean(InventoryGateway.class);

            assertThatThrownBy(() -> gateway.getStocksByProductIds(stockRequest)).isSameAs(badRequest);
            assertThatThrownBy(() -> gateway.getStocksByProductIds(stockRequest)).isSameAs(serverError);

            verify(inventoryClient(context), times(2)).getStocksByProductIds(stockRequest);
        });
    }

    @Test
    void writeIsNeverRetried() {
        contextRunner.run(context -> {
            FeignClientException unavailable = httpError(503);
            RetryableException timeout = timeout();
            doThrow(unavailable).doThrow(timeout).when(feignFileClient(context)).attachFile(fileAttach);
            FileClient fileClient = context.getBean(FileClient.class);

            // Existing error translation is preserved: the original exceptions surface unchanged.
            assertThatThrownBy(() -> fileClient.attachFile(fileAttach)).isSameAs(unavailable);
            assertThatThrownBy(() -> fileClient.attachFile(fileAttach)).isSameAs(timeout);

            // Two calls, two downstream requests: no hidden second attempt for either failure.
            verify(feignFileClient(context), times(2)).attachFile(fileAttach);
        });
    }

    // ---------------------------------------------------------------- circuit breaker

    @Test
    void fileBreakerOpensAfterRepeatedTransientFailuresAndThenFailsFast() {
        contextRunner.run(context -> {
            doThrow(httpError(503)).when(feignFileClient(context)).attachFile(any());
            FileClient fileClient = context.getBean(FileClient.class);

            for (int i = 0; i < MINIMUM_NUMBER_OF_CALLS; i++) {
                assertThatThrownBy(() -> fileClient.attachFile(fileAttach))
                        .isInstanceOf(FeignClientException.class);
            }

            assertThat(circuitBreaker(context, ResilientFileClient.FILE_SERVICE).getState())
                    .isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(() -> fileClient.attachFile(fileAttach))
                    .isInstanceOfSatisfying(FileServiceUnavailableException.class, exception -> {
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(exception.getCode()).isEqualTo(FileServiceUnavailableException.CODE);
                        assertThat(exception).hasCauseInstanceOf(CallNotPermittedException.class);
                    });
            // The rejected call never reached File-Service.
            verify(feignFileClient(context), times(MINIMUM_NUMBER_OF_CALLS)).attachFile(any());
        });
    }

    @Test
    void inventoryBreakerOpensAfterRepeatedTransientFailuresAndStopsTheRetryLoop() {
        contextRunner.run(context -> {
            when(inventoryClient(context).getStocksByProductIds(any())).thenThrow(timeout());
            InventoryGateway gateway = context.getBean(InventoryGateway.class);

            // 5 calls x 2 attempts = 10 recorded failures = minimumNumberOfCalls.
            for (int i = 0; i < MINIMUM_NUMBER_OF_CALLS / 2; i++) {
                assertThatThrownBy(() -> gateway.getStocksByProductIds(stockRequest))
                        .isInstanceOf(RetryableException.class);
            }

            assertThat(circuitBreaker(context, InventoryGateway.INVENTORY_SERVICE).getState())
                    .isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(() -> gateway.getStocksByProductIds(stockRequest))
                    .isInstanceOf(InventoryServiceUnavailableException.class)
                    .hasCauseInstanceOf(CallNotPermittedException.class);
            // Open breaker: not sent and not retried.
            verify(inventoryClient(context), times(MINIMUM_NUMBER_OF_CALLS)).getStocksByProductIds(any());
        });
    }

    @Test
    void clientErrorsNeverOpenTheBreakers() {
        contextRunner.run(context -> {
            FeignClientException notFound = httpError(404);
            FeignClientException badRequest = httpError(400);
            doThrow(notFound).when(feignFileClient(context)).attachFile(any());
            when(inventoryClient(context).getStocksByProductIds(any())).thenThrow(badRequest);
            FileClient fileClient = context.getBean(FileClient.class);
            InventoryGateway gateway = context.getBean(InventoryGateway.class);

            for (int i = 0; i < 3 * MINIMUM_NUMBER_OF_CALLS; i++) {
                assertThatThrownBy(() -> fileClient.attachFile(fileAttach)).isSameAs(notFound);
                assertThatThrownBy(() -> gateway.getStocksByProductIds(stockRequest)).isSameAs(badRequest);
            }

            for (String name : List.of(InventoryGateway.INVENTORY_SERVICE, ResilientFileClient.FILE_SERVICE)) {
                CircuitBreaker circuitBreaker = circuitBreaker(context, name);
                assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
                assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
            }
            verify(feignFileClient(context), times(3 * MINIMUM_NUMBER_OF_CALLS)).attachFile(any());
            verify(inventoryClient(context), times(3 * MINIMUM_NUMBER_OF_CALLS)).getStocksByProductIds(any());
        });
    }

    // ---------------------------------------------------------------- bulkhead

    @Test
    void fullBulkheadRejectsWithoutCallingDownstreamAndWithoutCountingAsFailure() {
        contextRunner.run(context -> {
            exhaust(bulkhead(context, ResilientFileClient.FILE_SERVICE));
            exhaust(bulkhead(context, InventoryGateway.INVENTORY_SERVICE));

            assertThatThrownBy(() -> context.getBean(FileClient.class).attachFile(fileAttach))
                    .isInstanceOf(FileServiceUnavailableException.class)
                    .hasCauseInstanceOf(BulkheadFullException.class);
            assertThatThrownBy(() -> context.getBean(InventoryGateway.class).getStocksByProductIds(stockRequest))
                    .isInstanceOf(InventoryServiceUnavailableException.class)
                    .hasCauseInstanceOf(BulkheadFullException.class);

            verify(feignFileClient(context), never()).attachFile(any());
            verify(inventoryClient(context), never()).getStocksByProductIds(any());
            for (String name : List.of(InventoryGateway.INVENTORY_SERVICE, ResilientFileClient.FILE_SERVICE)) {
                CircuitBreaker.Metrics metrics = circuitBreaker(context, name).getMetrics();
                assertThat(metrics.getNumberOfFailedCalls()).isZero();
                assertThat(metrics.getNumberOfSuccessfulCalls()).isZero();
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private static void exhaust(Bulkhead bulkhead) {
        int permits = bulkhead.getBulkheadConfig().getMaxConcurrentCalls();
        for (int i = 0; i < permits; i++) {
            assertThat(bulkhead.tryAcquirePermission()).isTrue();
        }
    }

    private static CircuitBreaker circuitBreaker(ApplicationContext context, String name) {
        return context.getBean(CircuitBreakerRegistry.class).circuitBreaker(name);
    }

    private static Bulkhead bulkhead(ApplicationContext context, String name) {
        return context.getBean(BulkheadRegistry.class).bulkhead(name);
    }

    private static InventoryClient inventoryClient(ApplicationContext context) {
        return context.getBean(InventoryClient.class);
    }

    private static FileClient feignFileClient(ApplicationContext context) {
        return context.getBean(ResilientFileClient.FEIGN_DELEGATE_QUALIFIER, FileClient.class);
    }

    private static FeignClientException httpError(int status) {
        return new FeignClientException(status, "Client#method", "HTTP " + status, null);
    }

    private static RetryableException timeout() {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://downstream/endpoint",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        return new RetryableException(
                -1,
                "Read timed out",
                Request.HttpMethod.POST,
                new SocketTimeoutException("Read timed out"),
                (Long) null,
                request
        );
    }

    private static void addMainApplicationYaml(ConfigurableApplicationContext context) {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("main-application-yml", new FileSystemResource("src/main/resources/application.yml"));
            sources.forEach(context.getEnvironment().getPropertySources()::addLast);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import({InventoryGateway.class, ResilientFileClient.class})
    static class TestConfig {

        @Bean
        InventoryClient inventoryClient() {
            return mock(InventoryClient.class);
        }

        /** Stands in for the Feign proxy, registered under the qualifier OpenFeign gives it. */
        @Bean(ResilientFileClient.FEIGN_DELEGATE_QUALIFIER)
        FileClient feignFileClient() {
            return mock(FileClient.class);
        }
    }
}
