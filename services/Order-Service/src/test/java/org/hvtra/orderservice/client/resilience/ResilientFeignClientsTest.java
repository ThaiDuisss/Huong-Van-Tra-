package org.hvtra.orderservice.client.resilience;

import feign.Client;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.hvtra.commonservice.common_core.client.FileClient;
import org.hvtra.commonservice.common_core.config.FeignConfig;
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.client.CustomBundleCatalogClient;
import org.hvtra.orderservice.client.CustomBundleInventoryClient;
import org.hvtra.orderservice.client.CustomerClient;
import org.hvtra.orderservice.client.InventoryClient;
import org.hvtra.orderservice.client.PaymentClient;
import org.hvtra.orderservice.client.resilience.DownstreamUnavailableException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Builds the real Feign clients the way the application does (Spring Cloud OpenFeign, the shared
 * FeignConfig error decoder, the resilience4j and timeout settings from application.yml) and
 * replaces only the HTTP transport with a scripted one. No database, broker or network is used.
 */
class ResilientFeignClientsTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            // application.yml takes this one from the environment; FeignConfig needs a value to sign with.
            .withPropertyValues("security.internal.header-secret=resilience-test-fixture-secret")
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    FeignAutoConfiguration.class,
                    CircuitBreakerAutoConfiguration.class,
                    RetryAutoConfiguration.class,
                    BulkheadAutoConfiguration.class
            ))
            .withUserConfiguration(FeignResilienceConfiguration.class, FeignClients.class);

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(
            clients = {
                    CatalogClient.class,
                    CustomBundleCatalogClient.class,
                    CustomBundleInventoryClient.class,
                    CustomerClient.class,
                    InventoryClient.class,
                    PaymentClient.class,
                    FileClient.class
            },
            defaultConfiguration = FeignConfig.class
    )
    static class FeignClients {

        @Bean
        ScriptedTransport scriptedTransport() {
            return new ScriptedTransport();
        }
    }

    // ---- (a) the breaker opens after repeated transient failures -------------------------------

    @Test
    void breakerOpensAfterRepeatedServerErrorsAndThenRefusesWithoutCallingDownstream() {
        contextRunner.run(context -> {
            ScriptedTransport transport = transport(context).respondingWith(status(503));
            InventoryClient inventory = context.getBean(InventoryClient.class);
            ThrowingCallable write = () -> inventory.releaseOrder(
                    new InventoryClient.ReleaseInventoryReservationOrderRequest(ID));

            for (int call = 0; call < 10; call++) {
                assertThatThrownBy(write)
                        .isExactlyInstanceOf(FeignClientException.class)
                        .extracting(failure -> ((FeignClientException) failure).getStatus())
                        .isEqualTo(503);
            }

            assertThat(breaker(context, "inventoryService").getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(write)
                    .isInstanceOfSatisfying(DownstreamUnavailableException.class, refused -> {
                        assertThat(refused.getReason()).isEqualTo(Reason.CIRCUIT_OPEN);
                        assertThat(refused.getService()).isEqualTo(DownstreamService.INVENTORY);
                        assertThat(refused.getStatus()).isEqualTo(503);
                        assertThat(refused.getMethodKey()).isEqualTo("InventoryClient#releaseOrder");
                    });
            assertThat(transport.requests()).hasSize(10);
        });
    }

    @Test
    void breakerOpensAfterRepeatedConnectionFailures() {
        contextRunner.run(context -> {
            ScriptedTransport transport = transport(context).respondingWith(connectionRefused());
            PaymentClient payment = context.getBean(PaymentClient.class);

            for (int call = 0; call < 10; call++) {
                assertThatThrownBy(() -> payment.cancelPendingPayment(ID)).isInstanceOf(RetryableException.class);
            }

            assertThat(breaker(context, "paymentService").getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(() -> payment.cancelPendingPayment(ID))
                    .isInstanceOf(DownstreamUnavailableException.class);
            assertThat(transport.requests()).hasSize(10);
        });
    }

    @Test
    void clientsOfTheSameServiceShareOneBreakerAndOtherServicesAreUnaffected() {
        contextRunner.run(context -> {
            ScriptedTransport transport = transport(context).respondingWith(status(500));
            CustomBundleInventoryClient bundleInventory = context.getBean(CustomBundleInventoryClient.class);

            for (int call = 0; call < 10; call++) {
                assertThatThrownBy(() -> bundleInventory.cancelPackingsByOrder(
                        new CustomBundleInventoryClient.CancelPackingsRequest(ID, "test")))
                        .isExactlyInstanceOf(FeignClientException.class);
            }

            assertThat(breaker(context, "inventoryService").getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(() -> context.getBean(InventoryClient.class).getReturnDispositionStatus(ID))
                    .isInstanceOf(DownstreamUnavailableException.class);

            transport.respondingWith(json("{}"));
            assertThat(context.getBean(PaymentClient.class).getPaymentByOrderId(ID)).isNotNull();
            assertThat(context.getBean(CatalogClient.class).findById(ID)).isNotNull();
            assertThat(breaker(context, "paymentService").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(breaker(context, "catalogService").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        });
    }

    // ---- (b) 4xx never opens the breaker -------------------------------------------------------

    @Test
    void clientErrorsNeverOpenTheBreakerAndKeepTheirOriginalException() {
        contextRunner.run(context -> {
            ScriptedTransport transport = transport(context).respondingWith(status(409));
            InventoryClient inventory = context.getBean(InventoryClient.class);

            for (int call = 0; call < 30; call++) {
                assertThatThrownBy(() -> inventory.applyReservation(
                        InventoryClient.ApplyInventoryReservationRequest.builder().orderId(ID).build()))
                        .isExactlyInstanceOf(FeignClientException.class)
                        .extracting(failure -> ((FeignClientException) failure).getStatus())
                        .isEqualTo(409);
            }

            CircuitBreaker breaker = breaker(context, "inventoryService");
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
            assertThat(transport.requests()).hasSize(30);
        });
    }

    // ---- (c) writes are sent exactly once ------------------------------------------------------

    @Test
    void writesAreNotRetriedOnTransientFailures() {
        contextRunner.run(context -> {
            List<ThrowingCallable> writes = List.of(
                    () -> context.getBean(PaymentClient.class).cancelPendingPayment(ID),
                    () -> context.getBean(PaymentClient.class).refundOrderReturn(
                            ID, new PaymentClient.PaymentRefundRequest(ID, BigDecimal.TEN)),
                    () -> context.getBean(PaymentClient.class).confirmManualPayment(
                            ID, new PaymentClient.ManualPaymentConfirmRequest("PAY-1", "note")),
                    () -> context.getBean(InventoryClient.class).fulfillSale(
                            new InventoryClient.FulfillSaleRequest(ID, ID, 1, List.of(), List.of())),
                    () -> context.getBean(InventoryClient.class).prepareBackorder(
                            new InventoryClient.AvailabilityEstimateRequest(ID, ID, "ORD-1", List.of())),
                    () -> context.getBean(CustomBundleInventoryClient.class).createPacking(
                            new CustomBundleInventoryClient.CreatePackingRequest(
                                    ID, "ORD-1", ID, ID, "bundle", ID, List.of())),
                    () -> context.getBean(CatalogClient.class).decrementSellableQuantity(
                            ID, new CatalogClient.SellableQuantityAdjustmentRequest(BigDecimal.ONE, ID, "sale", "op-1")),
                    () -> context.getBean(CustomerClient.class).updateCustomerPurchase(ID)
            );

            for (ResponseScript failure : List.of(status(503), status(504), connectionRefused())) {
                for (ThrowingCallable write : writes) {
                    ScriptedTransport transport = transport(context).respondingWith(failure);

                    assertThat(catchThrowable(write)).isNotNull();
                    assertThat(transport.requests()).hasSize(1);
                }
            }
            for (String instance : List.of("paymentService", "inventoryService", "catalogService", "customerService")) {
                assertThat(context.getBean(RetryRegistry.class).retry(instance)
                        .getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isZero();
            }
        });
    }

    // ---- (d) reads are retried once on a transient failure -------------------------------------

    @Test
    void readsAreRetriedOnceOnTransientFailureAndThenSucceed() {
        contextRunner.run(context -> {
            for (ResponseScript failure : List.of(status(502), status(503), status(504), connectionRefused())) {
                ScriptedTransport transport = transport(context).respondingWith(failure, json("{}"));

                assertThat(context.getBean(PaymentClient.class).getPaymentByOrderId(ID)).isNotNull();
                assertThat(transport.requests()).hasSize(2);
            }

            ScriptedTransport transport = transport(context).respondingWith(status(503), json("[]"));
            assertThat(context.getBean(InventoryClient.class).getStocksByVariantIds(
                    new InventoryClient.StockVariantBatchRequest(List.of(ID)))).isEmpty();
            assertThat(transport.requests()).hasSize(2);

            transport = transport(context).respondingWith(connectionRefused(), json("[]"));
            assertThat(context.getBean(CustomerClient.class).getCustomers("0900")).isEmpty();
            assertThat(transport.requests()).hasSize(2);

            transport = transport(context).respondingWith(status(503), json("{}"));
            assertThat(context.getBean(CustomBundleCatalogClient.class).getCustomBundleVariant()).isNotNull();
            assertThat(transport.requests()).hasSize(2);
        });
    }

    @Test
    void readGivesUpAfterTwoAttemptsAndRethrowsTheDownstreamError() {
        contextRunner.run(context -> {
            ScriptedTransport transport = transport(context).respondingWith(status(503));

            assertThatThrownBy(() -> context.getBean(CatalogClient.class).getProductVariantPosView(ID))
                    .isExactlyInstanceOf(FeignClientException.class);
            assertThat(transport.requests()).hasSize(2);
        });
    }

    @Test
    void readsAreNotRetriedOnClientErrorsOrPlainServerErrors() {
        contextRunner.run(context -> {
            for (int status : List.of(400, 401, 404, 409, 500)) {
                ScriptedTransport transport = transport(context).respondingWith(status(status), json("{}"));

                assertThatThrownBy(() -> context.getBean(CatalogClient.class).findById(ID))
                        .isExactlyInstanceOf(FeignClientException.class);
                assertThat(transport.requests()).hasSize(1);
            }
        });
    }

    @Test
    void readInsideADatabaseTransactionIsNotRetried() {
        contextRunner.run(context -> {
            ScriptedTransport transport = transport(context).respondingWith(status(503), json("{}"));

            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertThatThrownBy(() -> context.getBean(CatalogClient.class).getProductVariantPosView(ID))
                        .isExactlyInstanceOf(FeignClientException.class);
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            assertThat(transport.requests()).hasSize(1);
        });
    }

    @Test
    void readThatFailedAfterTheRetryBudgetIsNotRetried() {
        contextRunner
                .withPropertyValues("order.feign-resilience.retry-budget.catalogService=50ms")
                .run(context -> {
                    ScriptedTransport transport = transport(context).respondingWith(
                            request -> {
                                sleep(150);
                                return status(503).respond(request);
                            },
                            json("{}"));

                    assertThatThrownBy(() -> context.getBean(CatalogClient.class).findById(ID))
                            .isExactlyInstanceOf(FeignClientException.class);
                    assertThat(transport.requests()).hasSize(1);
                    assertThat(breaker(context, "catalogService").getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
                });
    }

    @Test
    void openBreakerIsNotRetried() {
        contextRunner.run(context -> {
            ScriptedTransport transport = transport(context).respondingWith(json("{}"));
            breaker(context, "catalogService").transitionToOpenState();

            assertThatThrownBy(() -> context.getBean(CatalogClient.class).findById(ID))
                    .isInstanceOf(DownstreamUnavailableException.class);
            assertThat(transport.requests()).isEmpty();
            assertThat(context.getBean(RetryRegistry.class).retry("catalogService")
                    .getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isZero();
        });
    }

    // ---- bulkhead ------------------------------------------------------------------------------

    @Test
    void fullBulkheadRefusesImmediatelyAndIsNotRecordedAsAFailure() {
        contextRunner
                .withPropertyValues("resilience4j.bulkhead.instances.paymentService.maxConcurrentCalls=1")
                .run(context -> {
                    CountDownLatch inFlight = new CountDownLatch(1);
                    CountDownLatch release = new CountDownLatch(1);
                    ScriptedTransport transport = transport(context).respondingWith(request -> {
                        inFlight.countDown();
                        await(release);
                        return json("{}").respond(request);
                    });
                    PaymentClient payment = context.getBean(PaymentClient.class);
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    try {
                        Future<?> first = executor.submit(() -> payment.cancelPendingPayment(ID));
                        assertThat(inFlight.await(5, TimeUnit.SECONDS)).isTrue();

                        assertThatThrownBy(() -> payment.getPaymentByOrderId(ID))
                                .isInstanceOfSatisfying(DownstreamUnavailableException.class, refused ->
                                        assertThat(refused.getReason()).isEqualTo(Reason.BULKHEAD_FULL));

                        release.countDown();
                        first.get(5, TimeUnit.SECONDS);
                    } finally {
                        release.countDown();
                        executor.shutdownNow();
                    }

                    assertThat(transport.requests()).hasSize(1);
                    CircuitBreaker breaker = breaker(context, "paymentService");
                    assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
                    assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
                });
    }

    // ---- configuration -------------------------------------------------------------------------

    @Test
    void everyClientHasExplicitConnectAndReadTimeouts() {
        contextRunner.run(context -> {
            ScriptedTransport transport = transport(context).respondingWith(
                    request -> json(request.url().contains("/packings/by-order/") ? "[]" : "{}").respond(request));

            context.getBean(PaymentClient.class).getPaymentByOrderId(ID);
            context.getBean(CustomerClient.class).getCustomerById(ID);
            context.getBean(CatalogClient.class).findById(ID);
            context.getBean(CustomBundleCatalogClient.class).getCustomBundleVariant();
            context.getBean(InventoryClient.class).getReturnDispositionStatus(ID);
            context.getBean(CustomBundleInventoryClient.class).findPackingsByOrder(ID);

            assertThat(transport.timeoutsMillis()).containsExactly(
                    List.of(1000L, 5000L),
                    List.of(1000L, 5000L),
                    List.of(1000L, 5000L),
                    List.of(1000L, 5000L),
                    List.of(2000L, 20000L),
                    List.of(1000L, 5000L)
            );
        });
    }

    @Test
    void everyOrderServiceClientIsGuardedAndTheUnusedSharedFileClientIsLeftAlone() {
        contextRunner.run(context -> {
            for (Class<?> client : List.of(CatalogClient.class, CustomBundleCatalogClient.class,
                    CustomBundleInventoryClient.class, CustomerClient.class, InventoryClient.class,
                    PaymentClient.class)) {
                assertThat(Proxy.getInvocationHandler(context.getBean(client)))
                        .as(client.getSimpleName())
                        .isInstanceOf(ResilientFeignInvocationHandler.class);
            }

            // Common-Service's FileClient is picked up by the org.hvtra scan but never called here.
            assertThat(Proxy.getInvocationHandler(context.getBean(FileClient.class)))
                    .isNotInstanceOf(ResilientFeignInvocationHandler.class);
        });
    }

    @Test
    void everyDownstreamServiceHasItsOwnConfiguredInstance() {
        contextRunner.run(context -> {
            CircuitBreakerRegistry breakers = context.getBean(CircuitBreakerRegistry.class);
            RetryRegistry retries = context.getBean(RetryRegistry.class);
            BulkheadRegistry bulkheads = context.getBean(BulkheadRegistry.class);

            for (DownstreamService service : DownstreamService.values()) {
                String instance = service.instanceName();
                assertThat(breakers.find(instance)).as(instance).isPresent();
                assertThat(retries.find(instance)).as(instance).isPresent();
                assertThat(bulkheads.find(instance)).as(instance).isPresent();

                var breakerConfig = breakers.circuitBreaker(instance).getCircuitBreakerConfig();
                assertThat(breakerConfig.getSlidingWindowSize()).isEqualTo(20);
                assertThat(breakerConfig.getMinimumNumberOfCalls()).isEqualTo(10);
                assertThat(breakerConfig.getFailureRateThreshold()).isEqualTo(50f);
                assertThat(retries.retry(instance).getRetryConfig().getMaxAttempts()).isEqualTo(2);
                assertThat(bulkheads.bulkhead(instance).getBulkheadConfig().getMaxWaitDuration()).isZero();
                assertThat(context.getBean(FeignResilienceProperties.class).getRetryBudget()).containsKey(instance);
            }

            // The slow-call threshold must stay below the Feign read timeout (5 s; Inventory 20 s).
            assertThat(breakers.circuitBreaker("paymentService").getCircuitBreakerConfig()
                    .getSlowCallDurationThreshold()).hasSeconds(4);
            assertThat(breakers.circuitBreaker("inventoryService").getCircuitBreakerConfig()
                    .getSlowCallDurationThreshold()).hasSeconds(15);
        });
    }

    // ---- scripted transport --------------------------------------------------------------------

    private static ScriptedTransport transport(AssertableApplicationContext context) {
        return context.getBean(ScriptedTransport.class);
    }

    private static CircuitBreaker breaker(AssertableApplicationContext context, String instance) {
        return context.getBean(CircuitBreakerRegistry.class).circuitBreaker(instance);
    }

    private static ResponseScript status(int status) {
        return request -> response(request, status, "{\"message\":\"downstream says " + status + "\"}");
    }

    private static ResponseScript json(String body) {
        return request -> response(request, 200, body);
    }

    private static ResponseScript connectionRefused() {
        return request -> {
            throw new ConnectException("Connection refused");
        };
    }

    private static Response response(Request request, int status, String body) {
        return Response.builder()
                .status(status)
                .reason("scripted")
                .request(request)
                .headers(Map.<String, Collection<String>>of("Content-Type", List.of("application/json")))
                .body(body, StandardCharsets.UTF_8)
                .build();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    interface ResponseScript {
        Response respond(Request request) throws IOException;
    }

    /** Stands in for the HTTP client: answers each request from a script; the last entry repeats. */
    static class ScriptedTransport implements Client {

        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private final List<List<Long>> timeoutsMillis = new CopyOnWriteArrayList<>();
        private volatile List<ResponseScript> script = List.of();

        ScriptedTransport respondingWith(ResponseScript... responses) {
            requests.clear();
            timeoutsMillis.clear();
            script = List.of(responses);
            return this;
        }

        List<Request> requests() {
            return requests;
        }

        List<List<Long>> timeoutsMillis() {
            return timeoutsMillis;
        }

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            int index;
            List<ResponseScript> current = script;
            synchronized (this) {
                requests.add(request);
                timeoutsMillis.add(List.of(
                        options.connectTimeoutUnit().toMillis(options.connectTimeout()),
                        options.readTimeoutUnit().toMillis(options.readTimeout())));
                index = Math.min(requests.size(), current.size()) - 1;
            }
            return current.get(index).respond(request);
        }
    }
}
