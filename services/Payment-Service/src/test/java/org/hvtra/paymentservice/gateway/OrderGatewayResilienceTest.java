package org.hvtra.paymentservice.gateway;

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
import org.hvtra.commonservice.common_core.exception.FeignClientException;
import org.hvtra.paymentservice.client.OrderClient;
import org.hvtra.paymentservice.dto.request.MarkOrderPaidRequest;
import org.hvtra.paymentservice.dto.response.OrderPaymentInfoResponse;
import org.hvtra.paymentservice.exception.OrderServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Slice test: chỉ nạp OrderGateway + auto-config Resilience4j/AOP với cấu hình THẬT trong
 * application.yml (không DataSource, Flyway, Kafka, Eureka, Feign), OrderClient là mock.
 */
@SpringBootTest(
        classes = OrderGatewayResilienceTest.SliceConfig.class,
        properties = "resilience4j.retry.instances.orderService.waitDuration=10ms"
)
class OrderGatewayResilienceTest {

    @Configuration(proxyBeanMethods = false)
    @Import(OrderGateway.class)
    @ImportAutoConfiguration({
            AopAutoConfiguration.class,
            RetryAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class,
            BulkheadAutoConfiguration.class,
            CircuitBreakersHealthIndicatorAutoConfiguration.class,
            HealthContributorAutoConfiguration.class,
            HealthEndpointAutoConfiguration.class
    })
    static class SliceConfig {
    }

    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @MockitoBean
    private OrderClient orderClient;

    @Autowired
    private OrderGateway orderGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private BulkheadRegistry bulkheadRegistry;

    @Autowired
    private HealthEndpoint healthEndpoint;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker(OrderGateway.ORDER_SERVICE);
        circuitBreaker.reset();
    }

    @Test
    void gatewayIsCalledThroughAopProxy() {
        assertThat(AopUtils.isAopProxy(orderGateway)).isTrue();
    }

    @Test
    void yamlConfigurationIsBoundToTheOrderServiceInstance() {
        CircuitBreakerConfig config = circuitBreaker.getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(config.getSlidingWindowSize()).isEqualTo(20);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        // Phải thấp hơn readTimeout 6 s của Feign client order-service.
        assertThat(config.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(4));

        assertThat(retryRegistry.retry(OrderGateway.ORDER_SERVICE).getRetryConfig().getMaxAttempts()).isEqualTo(2);
        assertThat(bulkheadRegistry.bulkhead(OrderGateway.ORDER_SERVICE).getBulkheadConfig().getMaxConcurrentCalls())
                .isEqualTo(20);
    }

    @Test
    void healthGroupExposesTheCircuitBreaker() {
        HealthComponent component = healthEndpoint.healthForPath("resilience", "circuitBreakers");

        assertThat(component).isInstanceOfSatisfying(Health.class,
                health -> assertThat(health.getDetails()).containsKey(OrderGateway.ORDER_SERVICE));
    }

    @Test
    void readIsRetriedOnceOnTransientFailure() {
        OrderPaymentInfoResponse response = new OrderPaymentInfoResponse();
        when(orderClient.getPaymentInfo(ORDER_ID))
                .thenThrow(upstream(503))
                .thenReturn(response);

        assertThat(orderGateway.getPaymentInfo(ORDER_ID)).isSameAs(response);

        verify(orderClient, times(2)).getPaymentInfo(ORDER_ID);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void readIsRetriedOnNetworkTimeoutAndThenPropagatesTheOriginalError() {
        RuntimeException timeout = new IllegalStateException(new SocketTimeoutException("Read timed out"));
        when(orderClient.getPaymentInfo(ORDER_ID)).thenThrow(timeout);

        assertThatThrownBy(() -> orderGateway.getPaymentInfo(ORDER_ID)).isSameAs(timeout);

        verify(orderClient, times(2)).getPaymentInfo(ORDER_ID);
    }

    @Test
    void readIsNotRetriedOnBusinessError() {
        FeignClientException notFound = upstream(404);
        when(orderClient.getPaymentInfo(ORDER_ID)).thenThrow(notFound);

        assertThatThrownBy(() -> orderGateway.getPaymentInfo(ORDER_ID)).isSameAs(notFound);

        verify(orderClient, times(1)).getPaymentInfo(ORDER_ID);
    }

    @Test
    void writeIsNeverRetried() {
        MarkOrderPaidRequest request = markPaidRequest();
        FeignClientException unavailable = upstream(503);
        doThrow(unavailable).when(orderClient).markOrderPaid(ORDER_ID, request);

        assertThatThrownBy(() -> orderGateway.markOrderPaid(ORDER_ID, request)).isSameAs(unavailable);

        verify(orderClient, times(1)).markOrderPaid(ORDER_ID, request);
    }

    @Test
    void breakerOpensAfterRepeatedTransientFailuresAndThenFailsFastWith503() {
        when(orderClient.getPaymentInfo(ORDER_ID)).thenThrow(upstream(503));

        // minimumNumberOfCalls = 10; mỗi lần gọi gateway = 2 attempt => 5 lần gọi là đủ mở breaker.
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> orderGateway.getPaymentInfo(ORDER_ID)).isInstanceOf(FeignClientException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(orderClient, times(10)).getPaymentInfo(ORDER_ID);

        // Breaker OPEN: từ chối ngay, không retry, không gọi Order-Service, cả đọc lẫn ghi.
        assertThatThrownBy(() -> orderGateway.getPaymentInfo(ORDER_ID))
                .isInstanceOfSatisfying(OrderServiceUnavailableException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo(OrderServiceUnavailableException.CODE);
                });
        assertThatThrownBy(() -> orderGateway.markOrderPaid(ORDER_ID, markPaidRequest()))
                .isInstanceOf(OrderServiceUnavailableException.class);

        verify(orderClient, times(10)).getPaymentInfo(ORDER_ID);
        verify(orderClient, times(0)).markOrderPaid(any(), any());
    }

    @Test
    void failedWritesAlsoOpenTheBreaker() {
        MarkOrderPaidRequest request = markPaidRequest();
        doThrow(upstream(500)).when(orderClient).markOrderPaid(ORDER_ID, request);

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> orderGateway.markOrderPaid(ORDER_ID, request))
                    .isInstanceOf(FeignClientException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        verify(orderClient, times(10)).markOrderPaid(ORDER_ID, request);
    }

    @Test
    void businessErrorsNeverOpenTheBreaker() {
        when(orderClient.getPaymentInfo(ORDER_ID)).thenThrow(upstream(404));
        MarkOrderPaidRequest request = markPaidRequest();
        doThrow(upstream(409)).when(orderClient).markOrderPaid(ORDER_ID, request);

        for (int i = 0; i < 30; i++) {
            assertThatThrownBy(() -> orderGateway.getPaymentInfo(ORDER_ID)).isInstanceOf(FeignClientException.class);
            assertThatThrownBy(() -> orderGateway.markOrderPaid(ORDER_ID, request))
                    .isInstanceOf(FeignClientException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        verify(orderClient, times(30)).getPaymentInfo(ORDER_ID);
    }

    @Test
    void fullBulkheadRejectsWith503WithoutCallingOrderServiceOrOpeningTheBreaker() {
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(OrderGateway.ORDER_SERVICE);
        int permits = bulkhead.getBulkheadConfig().getMaxConcurrentCalls();
        for (int i = 0; i < permits; i++) {
            assertThat(bulkhead.tryAcquirePermission()).isTrue();
        }
        try {
            assertThatThrownBy(() -> orderGateway.getPaymentInfo(ORDER_ID))
                    .isInstanceOf(OrderServiceUnavailableException.class);
            assertThatThrownBy(() -> orderGateway.markOrderPaid(ORDER_ID, markPaidRequest()))
                    .isInstanceOf(OrderServiceUnavailableException.class);
        } finally {
            for (int i = 0; i < permits; i++) {
                bulkhead.onComplete();
            }
        }

        verifyNoInteractions(orderClient);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    private static FeignClientException upstream(int status) {
        return new FeignClientException(status, "OrderClient#call", "HTTP " + status, null);
    }

    private static MarkOrderPaidRequest markPaidRequest() {
        return MarkOrderPaidRequest.builder()
                .paidAmount(new BigDecimal("100000"))
                .paymentCode("PAY-1")
                .build();
    }
}
