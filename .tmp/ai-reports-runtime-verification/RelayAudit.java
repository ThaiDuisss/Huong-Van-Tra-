import org.hvtra.apigateway.filter.InternalUserContextRelayFilter;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RelayAudit {
    private static final String PAYLOAD = "{\"name\":\"Báo cáo UTF-8\",\"version\":\"V1\"}";

    private static InternalUserContextRelayFilter filter() {
        InternalUserContextRelayFilter filter = new InternalUserContextRelayFilter();
        ReflectionTestUtils.setField(filter, "headerSecret", "isolated-audit-secret");
        return filter;
    }

    private static MockServerWebExchange exchange(String principal) {
        var request = MockServerHttpRequest.post("/api/orders/audit?mode=verify")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer isolated-harness-fixture")
                .header("X-User-ID", "forged")
                .header("X-User_Roles", "ADMIN")
                .header("X-Signature", "forged")
                .header("X-Request-ID", "audit-request")
                .header("X-Business-Header", "retained").body(PAYLOAD);
        var builder = MockServerWebExchange.builder(request);
        if (principal.equals("jwt")) {
            var jwt = Jwt.withTokenValue("isolated-harness-fixture").header("alg", "RS256")
                    .subject("00000000-0000-0000-0000-000000000001").build();
            builder.principal(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_SALE_STAFF"))));
        } else if (principal.equals("other")) {
            builder.principal(new UsernamePasswordAuthenticationToken("isolated-user", null, List.of()));
        }
        return builder.build();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void bodyAndResponse(String principal) {
        var exchange = exchange(principal);
        AtomicInteger constructed = new AtomicInteger();
        AtomicInteger subscriptions = new AtomicInteger();
        AtomicInteger bodySubscriptions = new AtomicInteger();
        Mono<Void> operation = filter().filter(exchange, forwarded -> {
            constructed.incrementAndGet();
            check(forwarded.getRequest().getURI().equals(exchange.getRequest().getURI()), "URI changed");
            var headers = forwarded.getRequest().getHeaders();
            check("retained".equals(headers.getFirst("X-Business-Header")), "business header dropped");
            check("application/json".equals(headers.getFirst("Content-Type")), "content type dropped");
            check("Bearer isolated-harness-fixture".equals(headers.getFirst("Authorization")), "authorization changed");
            if (!principal.equals("jwt")) {
                check(headers.getFirst("X-User-ID") == null && headers.getFirst("X-User_Roles") == null
                        && headers.getFirst("X-Signature") == null, "forged identity relayed");
            }
            return Mono.defer(() -> {
                subscriptions.incrementAndGet();
                return DataBufferUtils.join(Flux.defer(() -> {
                    bodySubscriptions.incrementAndGet();
                    return forwarded.getRequest().getBody();
                })).flatMap(buffer -> {
                    byte[] content = new byte[buffer.readableByteCount()];
                    buffer.read(content);
                    DataBufferUtils.release(buffer);
                    check(PAYLOAD.equals(new String(content, StandardCharsets.UTF_8)), "request body changed");
                    forwarded.getResponse().setStatusCode(HttpStatus.CONFLICT);
                    forwarded.getResponse().getHeaders().set("X-Downstream", "retained");
                    return forwarded.getResponse().writeWith(Mono.just(forwarded.getResponse().bufferFactory().wrap(content)));
                });
            });
        });
        check(constructed.get() == 0, "downstream eagerly constructed");
        operation.block();
        check(constructed.get() == 1 && subscriptions.get() == 1 && bodySubscriptions.get() == 1, "downstream/body ran more than once");
        check(exchange.getResponse().getStatusCode() == HttpStatus.CONFLICT, "status lost");
        check("retained".equals(exchange.getResponse().getHeaders().getFirst("X-Downstream")), "response header lost");
        check(PAYLOAD.equals(exchange.getResponse().getBodyAsString().block()), "response body lost");
        System.out.println("PASS body/status/headers + single subscription: " + principal);
    }

    public static void main(String[] args) {
        for (String principal : List.of("jwt", "absent", "other")) bodyAndResponse(principal);
        AtomicInteger errors = new AtomicInteger();
        var sentinel = new IllegalStateException("isolated downstream error");
        try {
            filter().filter(exchange("jwt"), forwarded -> {
                errors.incrementAndGet();
                return Mono.error(sentinel);
            }).block();
            throw new AssertionError("downstream error swallowed");
        } catch (IllegalStateException error) {
            check(error == sentinel, "downstream error changed");
        }
        check(errors.get() == 1, "error triggered fallback");
        System.out.println("PASS exact error propagation without fallback");

        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger cancellationCalls = new AtomicInteger();
        var pending = filter().filter(exchange("jwt"), forwarded -> {
            cancellationCalls.incrementAndGet();
            return Mono.<Void>never().doOnCancel(cancellations::incrementAndGet);
        }).subscribe();
        pending.dispose();
        check(cancellationCalls.get() == 1 && cancellations.get() == 1, "cancellation repeated/dropped");
        System.out.println("PASS cancellation propagation without fallback");

        AtomicInteger attempts = new AtomicInteger();
        filter().filter(exchange("jwt"), forwarded -> attempts.incrementAndGet() == 1
                ? Mono.error(sentinel) : Mono.empty()).retry(1).block();
        check(attempts.get() == 2, "explicit retry attempts changed");
        System.out.println("PASS explicit retry(1) has exactly two attempts");
        System.out.println("SUPPLEMENTAL_RELAY_CASES=6 PASS");
    }
}
