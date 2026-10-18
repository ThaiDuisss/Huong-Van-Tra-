package org.hvtra.apigateway.config;

import org.hvtra.apigateway.filter.InternalUserContextRelayFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Gateway relay permission (client role của client API) sang service với chữ ký riêng. */
class PermissionRelayTest {

    private static final String SECRET = "test-gateway-relay-secret";

    private InternalUserContextRelayFilter filter() {
        InternalUserContextRelayFilter filter = new InternalUserContextRelayFilter();
        ReflectionTestUtils.setField(filter, "headerSecret", SECRET);
        ReflectionTestUtils.setField(filter, "permissionClientId", "hvtra-pos");
        return filter;
    }

    private MockServerHttpRequest request() {
        return MockServerHttpRequest.get("/api/orders/pos/checkout")
                .header("Authorization", "Bearer jwt")
                .header("X-User-Permissions", "permission:manage")
                .header("X-Permissions-Signature", "forged")
                .header("X-Request-ID", "request-id")
                .build();
    }

    @Test
    void relaysSortedPermissionsOfTheApiClientOnlyAndSignsThem() throws Exception {
        String user = UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue("jwt").header("alg", "RS256").subject(user)
                .claim("realm_access", Map.of("roles", List.of("SALE_STAFF")))
                .claim("resource_access", Map.of(
                        "hvtra-pos", Map.of("roles", List.of("order:view", "order:create")),
                        "account", Map.of("roles", List.of("manage-account"))))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_SALE_STAFF")));
        MockServerWebExchange exchange = MockServerWebExchange.builder(request()).principal(auth).build();
        AtomicReference<ServerWebExchange> received = new AtomicReference<>();

        filter().filter(exchange, forwarded -> { received.set(forwarded); return Mono.empty(); }).block();

        var headers = received.get().getRequest().getHeaders();
        assertEquals(List.of("order:create,order:view"), headers.get("X-User-Permissions"));
        assertEquals(List.of("SALE_STAFF"), headers.get("X-User_Roles"));
        assertEquals(hmac(String.join("\n", "permissions", user, "order:create,order:view", "request-id")),
                headers.getFirst("X-Permissions-Signature"));
    }

    @Test
    void userWithoutClientRolesGetsEmptyButSignedPermissionHeader() throws Exception {
        String user = UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue("jwt").header("alg", "RS256").subject(user).build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        MockServerWebExchange exchange = MockServerWebExchange.builder(request()).principal(auth).build();
        AtomicReference<ServerWebExchange> received = new AtomicReference<>();

        filter().filter(exchange, forwarded -> { received.set(forwarded); return Mono.empty(); }).block();

        var headers = received.get().getRequest().getHeaders();
        assertEquals(List.of(""), headers.get("X-User-Permissions"));
        assertEquals(hmac(String.join("\n", "permissions", user, "", "request-id")),
                headers.getFirst("X-Permissions-Signature"));
    }

    private static String hmac(String raw) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
