package org.hvtra.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Primary
@Component("customerRateLimiter")
@Slf4j
public class CustomerRateLimiter implements RateLimiter<CustomerRateLimiter.Config> {
    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List> rateLimitScript;

    public CustomerRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.rateLimitScript = buildScript();
        this.redisTemplate = redisTemplate;
    }


    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        RateLimitPolicy policy = resolvePolicy(id);

        String redisKey = "rate_limit" + routeId + ":" + id;
        List<String> keys = List.of(redisKey);
        List<String> args = List.of(
                String.valueOf(policy.limit),
                String.valueOf(policy.windowSecond));

        return redisTemplate.execute(rateLimitScript, keys, args)
                .next()
                .map(result -> {
                    long allowed = toLong(result.get(0));
                    long remaining = toLong(result.get(1));
                    long ttl = toLong(result.get(2));

                    Map<String, String> headers = Map.of(
                            "X-RateLimit-Type", policy.type(),
                            "X-RateLimit-Limit", String.valueOf(policy.limit()),
                            "X-RateLimit-Remaining", String.valueOf(Math.max(remaining, 0)),
                            "X-RateLimit-Reset-Seconds", String.valueOf(ttl)
                    );

                    return new Response(allowed == 1, headers);
                })
                .onErrorResume(ex ->
                        // Redis lỗi thì tạm thời cho đi qua để tránh sập hệ thống
                        Mono.just(new Response(true, Map.of(
                                "X-RateLimit-Error", "redis-error"
                        )))
                );
    }
    private RateLimitPolicy resolvePolicy(String key) {
        if (key != null && key.startsWith("admin:")) {
            return new RateLimitPolicy("ADMIN", 1000, 60);
        }

        if (key != null && key.startsWith("staff:")) {
            return new RateLimitPolicy("STAFF", 300, 60);
        }

        if (key != null && key.startsWith("user:")) {
            return new RateLimitPolicy("USER", 200, 60);
        }

        return new RateLimitPolicy("GUEST", 200, 60);
    }

    private RedisScript<List> buildScript() {
        String luaScript = """
                local key = KEYS[1]
                local limit = tonumber(ARGV[1])
                local window = tonumber(ARGV[2])

                local current = redis.call('INCR', key)

                if current == 1 then
                    redis.call('EXPIRE', key, window)
                end

                local ttl = redis.call('TTL', key)
                local remaining = limit - current

                if current <= limit then
                    return {1, remaining, ttl}
                else
                    return {0, remaining, ttl}
                end
                """;
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(luaScript);
        script.setResultType(List.class);
        return script;
    }
    private long toLong(Object value) {
        if (value instanceof Long l) {
            return l;
        }

        if (value instanceof Integer i) {
            return i.longValue();
        }

        return Long.parseLong(value.toString());
    }


    @Override
    public Map<String, Config> getConfig() {
        return Map.of();
    }

    @Override
    public Class<Config> getConfigClass() {
        return Config.class;
    }

    @Override
    public Config newConfig() {
        return new Config();
    }

    public static class Config {
    }
    private record RateLimitPolicy(
            String type,
            int limit,
            int windowSecond
    ){ }
}
