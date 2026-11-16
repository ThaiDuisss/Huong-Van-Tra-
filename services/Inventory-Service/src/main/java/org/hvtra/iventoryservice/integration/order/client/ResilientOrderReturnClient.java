package org.hvtra.iventoryservice.integration.order.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnInventoryCommandResponse;
import org.hvtra.iventoryservice.integration.order.dto.OrderReturnResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resilience4j decorator around the Order-Service Feign proxy; {@code @Primary}, so it is what every
 * {@code OrderReturnClient} injection point receives. Nesting: Retry( CircuitBreaker( Bulkhead( call ) ) ).
 *
 * <p>Both endpoints are GETs that only read the return / the inventory command, so both are retried
 * on transient failures. They gate stock movements, therefore there is deliberately NO fallback: an
 * unverified return or command must never be treated as authorised. Failures propagate unchanged
 * ({@code FeignClientException}, {@code RetryableException}); an open breaker / full bulkhead
 * surfaces as {@code CallNotPermittedException} / {@code BulkheadFullException}. None of those is a
 * "permanent failure" for the return-disposition Kafka consumer, so the record is redelivered
 * instead of being acknowledged as FAILED.
 */
@Primary
@Component
@CircuitBreaker(name = ResilientOrderReturnClient.INSTANCE)
@Bulkhead(name = ResilientOrderReturnClient.INSTANCE)
public class ResilientOrderReturnClient implements OrderReturnClient {

    public static final String INSTANCE = "orderService";

    private final OrderReturnClient delegate;

    public ResilientOrderReturnClient(@Qualifier(FEIGN_QUALIFIER) OrderReturnClient delegate) {
        this.delegate = delegate;
    }

    @Retry(name = INSTANCE)
    @Override
    public OrderReturnResponse getReturnById(UUID returnId) {
        return delegate.getReturnById(returnId);
    }

    @Retry(name = INSTANCE)
    @Override
    public OrderReturnInventoryCommandResponse getInventoryDisposition(UUID returnId, UUID commandId) {
        return delegate.getInventoryDisposition(returnId, commandId);
    }
}
