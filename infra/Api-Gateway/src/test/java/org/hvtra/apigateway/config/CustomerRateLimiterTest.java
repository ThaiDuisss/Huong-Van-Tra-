package org.hvtra.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerRateLimiterTest {

    private final ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
    private final CustomerRateLimiter rateLimiter = new CustomerRateLimiter(redisTemplate);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void allowedRequestDoesNotAddResponseHeaders() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(1L, 199L, 60L)));

        RateLimiter.Response response = rateLimiter.isAllowed("identity-service", "user:123").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).isEmpty();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectedRequestKeepsRateLimitHeaders() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(List.of(0L, -1L, 42L)));

        RateLimiter.Response response = rateLimiter.isAllowed("identity-service", "user:123").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getHeaders())
                .containsEntry("X-RateLimit-Type", "USER")
                .containsEntry("X-RateLimit-Limit", "200")
                .containsEntry("X-RateLimit-Remaining", "0")
                .containsEntry("X-RateLimit-Reset-Seconds", "42");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void redisFailureAllowsRequestWithoutResponseHeaders() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new IllegalStateException("redis unavailable")));

        RateLimiter.Response response = rateLimiter.isAllowed("identity-service", "user:123").block();

        assertThat(response).isNotNull();
        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getHeaders()).isEmpty();
    }
}
