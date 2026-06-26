package org.hvtra.customer.infrastructure.gateway;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.customer.constant.ErrorCode;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.infrastructure.client.OrderClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderGateway {

    private static final String ORDER_SERVICE = "orderService";

    private final OrderClient orderClient;

    @Retry(name = ORDER_SERVICE)
    @CircuitBreaker(
            name = ORDER_SERVICE,
            fallbackMethod = "handleOrderFailure"
    )
    @Bulkhead(name = ORDER_SERVICE)
    public OrderClient.OrderResponse getOrderById(UUID orderId) {
        return orderClient.getOrderById(orderId);
    }

    private OrderClient.OrderResponse handleOrderFailure(
            UUID orderId,
            CallNotPermittedException exception
    ) {
        log.warn(
                "Order Circuit Breaker is OPEN. orderId={}",
                orderId
        );

        throw new CustomerServiceException(
                ErrorCode.ORDER_SERVICE_UNAVAILABLE,
                "Order Service đang tạm ngắt do lỗi liên tục.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private OrderClient.OrderResponse handleOrderFailure(
            UUID orderId,
            BulkheadFullException exception
    ) {
        log.warn(
                "Order Bulkhead is full. orderId={}",
                orderId
        );

        throw new CustomerServiceException(
                ErrorCode.ORDER_SERVICE_UNAVAILABLE,
                "Hệ thống đang có quá nhiều yêu cầu kiểm tra đơn hàng.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private OrderClient.OrderResponse handleOrderFailure(
            UUID orderId,
            Throwable exception
    ) {
        log.error(
                "Cannot get order from Order Service. orderId={}",
                orderId,
                exception
        );


        throw new CustomerServiceException(
                ErrorCode.ORDER_SERVICE_UNAVAILABLE,
                "Không thể kiểm tra đơn hàng lúc này. Vui lòng thử lại.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}
