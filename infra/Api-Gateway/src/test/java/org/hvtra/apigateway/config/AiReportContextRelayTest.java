package org.hvtra.apigateway.config;

import org.hvtra.apigateway.filter.InternalUserContextRelayFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AiReportContextRelayTest {
    private static final String SECRET = "test-gateway-relay-secret";

    InternalUserContextRelayFilter filter() {
        InternalUserContextRelayFilter filter = new InternalUserContextRelayFilter();
        ReflectionTestUtils.setField(filter, "headerSecret", SECRET);
        return filter;
    }

    MockServerHttpRequest request() {
        return MockServerHttpRequest.get("/api/recommendation/ai-reports")
                .header("Authorization", "Bearer jwt")
                .header("X-User-ID", "forged-user").header("X-User_Roles", "ADMIN")
                .header("X-Signature", "forged-signature").header("X-Request-ID", "request-id").build();
    }

    @Test void jwtReplacesForgedContextAndInvokesDownstreamOnce() throws Exception {
        String user = UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue("jwt").header("alg", "RS256").subject(user).build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_SALE_STAFF")));
        MockServerWebExchange exchange = MockServerWebExchange.builder(request()).principal(auth).build();
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ServerWebExchange> received = new AtomicReference<>();
        filter().filter(exchange, forwarded -> { calls.incrementAndGet(); received.set(forwarded); return Mono.empty(); }).block();
        assertEquals(1, calls.get());
        var headers = received.get().getRequest().getHeaders();
        assertEquals(List.of(user), headers.get("X-User-ID"));
        assertEquals(List.of("SALE_STAFF"), headers.get("X-User_Roles"));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal(String.join("\n", user, "SALE_STAFF", "request-id", "Bearer jwt")
                .getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, headers.getFirst("X-Signature"));
    }

    @Test void missingPrincipalCannotRelayForgedInternalHeaders() {
        AtomicInteger calls = new AtomicInteger();
        filter().filter(MockServerWebExchange.from(request()), forwarded -> {
            calls.incrementAndGet();
            assertNull(forwarded.getRequest().getHeaders().getFirst("X-User-ID"));
            assertNull(forwarded.getRequest().getHeaders().getFirst("X-User_Roles"));
            assertNull(forwarded.getRequest().getHeaders().getFirst("X-Signature"));
            return Mono.empty();
        }).block();
        assertEquals(1, calls.get());
    }

    @Test void nonJwtAuthenticationDoesNotBecomeSignedUserContext() {
        var auth = new UsernamePasswordAuthenticationToken("user", null, List.of());
        var exchange = MockServerWebExchange.builder(request()).principal(auth).build();
        AtomicInteger calls = new AtomicInteger();
        filter().filter(exchange, forwarded -> {
            calls.incrementAndGet();
            assertNull(forwarded.getRequest().getHeaders().getFirst("X-Signature"));
            return Mono.empty();
        }).block();
        assertEquals(1, calls.get());
    }
}
