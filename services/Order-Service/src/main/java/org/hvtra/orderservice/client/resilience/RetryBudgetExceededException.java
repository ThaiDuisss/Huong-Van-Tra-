package org.hvtra.orderservice.client.resilience;

/**
 * Carries a failure out of the retry loop unchanged when the attempt took too long to be worth
 * repeating (typically a read timeout). Never leaves {@link ResilientFeignInvocationHandler}.
 */
class RetryBudgetExceededException extends RuntimeException {

    RetryBudgetExceededException(Throwable cause) {
        super(cause);
    }
}
