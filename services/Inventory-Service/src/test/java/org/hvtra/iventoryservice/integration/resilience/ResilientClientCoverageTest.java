package org.hvtra.iventoryservice.integration.resilience;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.hvtra.commonservice.common_core.client.FileClient;
import org.hvtra.iventoryservice.integration.catalog.client.CatalogClient;
import org.hvtra.iventoryservice.integration.catalog.client.ResilientCatalogClient;
import org.hvtra.iventoryservice.integration.file.client.ResilientFileClient;
import org.hvtra.iventoryservice.integration.identity.client.IdentityClient;
import org.hvtra.iventoryservice.integration.identity.client.ResilientIdentityClient;
import org.hvtra.iventoryservice.integration.order.client.OrderReturnClient;
import org.hvtra.iventoryservice.integration.order.client.ResilientOrderReturnClient;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the classification itself: every Feign method is protected, and exactly the methods known
 * to be writes are excluded from retry. Adding a Feign method forces a conscious decision here.
 */
class ResilientClientCoverageTest {

    /** Non-idempotent (or not provably idempotent) downstream writes: never retried. */
    private static final Set<String> CATALOG_WRITES = Set.of("createIngredient", "addIngredientsToSupplier");

    @Test
    void catalogDecoratorProtectsEveryMethodAndRetriesOnlyReads() {
        assertDecorator(CatalogClient.class, ResilientCatalogClient.class, "catalogService", CATALOG_WRITES);
    }

    @Test
    void orderDecoratorProtectsEveryMethodAndRetriesOnlyReads() {
        assertDecorator(OrderReturnClient.class, ResilientOrderReturnClient.class, "orderService", Set.of());
    }

    @Test
    void identityDecoratorProtectsEveryMethodAndRetriesOnlyReads() {
        assertDecorator(IdentityClient.class, ResilientIdentityClient.class, "identityService", Set.of());
    }

    @Test
    void fileDecoratorProtectsAttachAndNeverRetriesIt() {
        assertDecorator(FileClient.class, ResilientFileClient.class, "fileService", Set.of("attachFile"));
    }

    private static void assertDecorator(Class<?> feignInterface, Class<?> decorator, String instance, Set<String> writes) {
        // The raw Feign bean must not compete with the decorator for unqualified injection.
        assertThat(feignInterface.getAnnotation(FeignClient.class).primary()).isFalse();
        assertThat(decorator.isAnnotationPresent(Primary.class)).isTrue();
        assertThat(feignInterface.isAssignableFrom(decorator)).isTrue();

        // Class-level breaker + bulkhead cover every public method of the decorator.
        assertThat(decorator.getAnnotation(CircuitBreaker.class).name()).isEqualTo(instance);
        assertThat(decorator.getAnnotation(CircuitBreaker.class).fallbackMethod()).isEmpty();
        assertThat(decorator.getAnnotation(Bulkhead.class).name()).isEqualTo(instance);
        assertThat(decorator.getAnnotation(Bulkhead.class).type()).isEqualTo(Bulkhead.Type.SEMAPHORE);
        assertThat(decorator.isAnnotationPresent(Retry.class))
                .as("retry must be opted into per method, never for the whole class")
                .isFalse();

        for (Method feignMethod : feignInterface.getDeclaredMethods()) {
            if (Modifier.isStatic(feignMethod.getModifiers())) {
                continue;
            }
            Method override = declaredMethod(decorator, feignMethod);
            assertThat(Modifier.isPublic(override.getModifiers())).isTrue();
            assertThat(Modifier.isFinal(override.getModifiers()))
                    .as("%s must stay proxyable", feignMethod.getName())
                    .isFalse();

            Retry retry = override.getAnnotation(Retry.class);
            if (writes.contains(feignMethod.getName())) {
                assertThat(retry).as("write %s must not be retried", feignMethod.getName()).isNull();
            } else {
                assertThat(retry).as("read %s should be retried", feignMethod.getName()).isNotNull();
                assertThat(retry.name()).isEqualTo(instance);
                assertThat(retry.fallbackMethod()).isEmpty();
            }
        }
    }

    private static Method declaredMethod(Class<?> decorator, Method feignMethod) {
        try {
            return decorator.getDeclaredMethod(feignMethod.getName(), feignMethod.getParameterTypes());
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(decorator.getSimpleName() + " does not override " + feignMethod, exception);
        }
    }
}
