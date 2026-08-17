package org.hvtra.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyResolverConfigTest {

    private final RateLimitKeyResolverConfig config = new RateLimitKeyResolverConfig();

    @Test
    void getIpUsesFirstForwardedAddressWhenPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/identity/me")
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.5"));

        assertThat(config.getIp(exchange)).isEqualTo("203.0.113.10");
    }
}
