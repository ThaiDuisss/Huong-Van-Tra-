package org.hvtra.orderservice.client.resilience;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Feign client method as a side-effect-free lookup that may be sent a second time.
 *
 * <p>Only methods carrying this annotation are ever retried by {@link ResilientFeignInvocationHandler}.
 * A method without it is treated as a write and is attempted exactly once, whatever its HTTP verb,
 * so a newly added endpoint is safe by default. Several lookups here are POSTs (batch reads), which
 * is why the HTTP verb is not used to decide.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentRead {
}
