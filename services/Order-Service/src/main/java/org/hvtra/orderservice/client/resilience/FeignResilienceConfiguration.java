package org.hvtra.orderservice.client.resilience;

import feign.InvocationHandlerFactory;
import feign.Target;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.InvocationHandler;
import java.time.Duration;
import java.util.Optional;

/**
 * Puts every Feign client of a known {@link DownstreamService} behind that service's Resilience4j
 * instance. Hooking in at the Feign builder means no call site can reach the HTTP client unguarded.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(FeignResilienceProperties.class)
public class FeignResilienceConfiguration {

    @Bean
    public FeignBuilderCustomizer resilientFeignBuilderCustomizer(
            CircuitBreakerRegistry circuitBreakerRegistry,
            BulkheadRegistry bulkheadRegistry,
            RetryRegistry retryRegistry,
            FeignResilienceProperties properties
    ) {
        InvocationHandlerFactory factory = resilientInvocationHandlerFactory(
                circuitBreakerRegistry, bulkheadRegistry, retryRegistry, properties);
        return builder -> builder.invocationHandlerFactory(factory);
    }

    static InvocationHandlerFactory resilientInvocationHandlerFactory(
            CircuitBreakerRegistry circuitBreakerRegistry,
            BulkheadRegistry bulkheadRegistry,
            RetryRegistry retryRegistry,
            FeignResilienceProperties properties
    ) {
        InvocationHandlerFactory plain = new InvocationHandlerFactory.Default();
        return (target, dispatch) -> {
            InvocationHandler delegate = plain.create(target, dispatch);
            Optional<DownstreamService> service = DownstreamService.fromFeignName(target.name());
            if (service.isEmpty()) {
                log.warn("Feign client {} ({}) has no Resilience4j instance and is called unguarded",
                        target.type().getSimpleName(), target.name());
                return delegate;
            }
            String instance = service.get().instanceName();
            return new ResilientFeignInvocationHandler(
                    target,
                    delegate,
                    service.get(),
                    circuitBreakerRegistry.find(instance).orElseThrow(() -> notConfigured("circuitbreaker", target)),
                    bulkheadRegistry.find(instance).orElseThrow(() -> notConfigured("bulkhead", target)),
                    retryRegistry.find(instance).orElseThrow(() -> notConfigured("retry", target)),
                    retryBudget(properties, instance)
            );
        };
    }

    private static Duration retryBudget(FeignResilienceProperties properties, String instance) {
        Duration budget = properties.getRetryBudget().get(instance);
        if (budget == null) {
            throw new IllegalStateException("Missing order.feign-resilience.retry-budget." + instance);
        }
        return budget;
    }

    private static IllegalStateException notConfigured(String module, Target<?> target) {
        return new IllegalStateException("Missing resilience4j." + module + ".instances entry for Feign client "
                + target.type().getSimpleName() + " (" + target.name() + ")");
    }
}
