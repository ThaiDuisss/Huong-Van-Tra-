package org.hvtra.orderservice.client.resilience;

import feign.Target;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.orderservice.client.resilience.DownstreamUnavailableException.Reason;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.time.Duration;

/**
 * Runs every method of a Feign client as Retry( CircuitBreaker( Bulkhead( HTTP call ) ) ).
 *
 * <ul>
 *   <li>Circuit breaker and bulkhead guard every call.</li>
 *   <li>Retry wraps only methods marked {@link IdempotentRead}, only outside a database transaction
 *       (so a transaction never waits longer than one attempt), and only while the failed attempt
 *       stayed within the retry budget.</li>
 *   <li>A call refused by the breaker or the bulkhead surfaces as
 *       {@link DownstreamUnavailableException}; every other failure is rethrown untouched.</li>
 * </ul>
 *
 * It sits inside the Feign proxy itself, so there is no Spring AOP proxy to bypass by self-invocation.
 */
@Slf4j
class ResilientFeignInvocationHandler implements InvocationHandler {

    private final Target<?> target;
    private final InvocationHandler delegate;
    private final DownstreamService service;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final Retry retry;
    private final Duration retryBudget;

    ResilientFeignInvocationHandler(
            Target<?> target,
            InvocationHandler delegate,
            DownstreamService service,
            CircuitBreaker circuitBreaker,
            Bulkhead bulkhead,
            Retry retry,
            Duration retryBudget
    ) {
        this.target = target;
        this.delegate = delegate;
        this.service = service;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.retry = retry;
        this.retryBudget = retryBudget;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class || method.isDefault()) {
            return delegate.invoke(proxy, method, args);
        }

        CheckedSupplier<Object> guarded = CircuitBreaker.decorateCheckedSupplier(
                circuitBreaker,
                Bulkhead.decorateCheckedSupplier(bulkhead, () -> delegate.invoke(proxy, method, args))
        );

        try {
            return mayRetry(method)
                    ? retry.executeCheckedSupplier(withinRetryBudget(guarded))
                    : guarded.get();
        } catch (RetryBudgetExceededException exception) {
            throw translate(exception.getCause(), method);
        } catch (Throwable throwable) {
            throw translate(throwable, method);
        }
    }

    private boolean mayRetry(Method method) {
        return method.isAnnotationPresent(IdempotentRead.class)
                && !TransactionSynchronizationManager.isActualTransactionActive();
    }

    private CheckedSupplier<Object> withinRetryBudget(CheckedSupplier<Object> call) {
        long deadline = System.nanoTime() + retryBudget.toNanos();
        return () -> {
            try {
                return call.get();
            } catch (Throwable throwable) {
                if (System.nanoTime() - deadline > 0) {
                    throw new RetryBudgetExceededException(throwable);
                }
                throw throwable;
            }
        };
    }

    private Throwable translate(Throwable failure, Method method) {
        if (failure instanceof CallNotPermittedException) {
            return rejected(Reason.CIRCUIT_OPEN, method, failure);
        }
        if (failure instanceof BulkheadFullException) {
            return rejected(Reason.BULKHEAD_FULL, method, failure);
        }
        return failure;
    }

    private DownstreamUnavailableException rejected(Reason reason, Method method, Throwable cause) {
        String methodKey = target.type().getSimpleName() + "#" + method.getName();
        log.warn("{} call refused locally ({}): instance={}", methodKey, reason, service.instanceName());
        return new DownstreamUnavailableException(service, reason, methodKey, cause);
    }
}
