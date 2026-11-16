package org.hvtra.iventoryservice.integration.identity.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resilience4j decorator around the Identity-Service Feign proxy; {@code @Primary}, so it is what
 * every {@code IdentityClient} injection point receives. Nesting: Retry( CircuitBreaker( Bulkhead( call ) ) ).
 *
 * <p>The only endpoint is a POST used as a pure lookup (user display names by Keycloak subject), so
 * it is safe to retry. Both call sites already catch and render the list without names, which is
 * the degraded result; with the breaker open they degrade immediately instead of waiting on a
 * timeout. No fallback is added here so that the callers keep logging the real cause.
 */
@Primary
@Component
@CircuitBreaker(name = ResilientIdentityClient.INSTANCE)
@Bulkhead(name = ResilientIdentityClient.INSTANCE)
public class ResilientIdentityClient implements IdentityClient {

    public static final String INSTANCE = "identityService";

    private final IdentityClient delegate;

    public ResilientIdentityClient(@Qualifier(FEIGN_QUALIFIER) IdentityClient delegate) {
        this.delegate = delegate;
    }

    @Retry(name = INSTANCE)
    @Override
    public List<UserBriefResponse> getUsersBatch(List<String> keycloakSubjects) {
        return delegate.getUsersBatch(keycloakSubjects);
    }
}
