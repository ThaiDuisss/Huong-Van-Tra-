package org.hvtra.apigateway.filter;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.apigateway.utils.GatewayConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class InternalUserContextRelayFilter implements GlobalFilter, Ordered {
    @Value("${security.internal.header-secret}")
    private String headerSecret;

    @Value("${spring.application.name:Api-Gateway}")
    private String applicationName;

    @PostConstruct
    void logInternalAuthConfiguration() {
        log.info(
                "Internal auth secret loaded: service={}, property=security.internal.header-secret, length={}, fingerprint={}",
                applicationName,
                headerSecret.length(),
                secretFingerprint()
        );
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(Authentication.class)
                .flatMap( authenticator ->  {
                    if(!(authenticator instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
                        return chain.filter(removeInternalHeaders(exchange));
                    }
                    Jwt jwt = jwtAuthenticationToken.getToken();
                    String userId = value(jwt.getSubject());
                    String authorization = value(exchange.getRequest()
                            .getHeaders()
                            .getFirst(GatewayConstants.HEADER_AUTHOR));

                    String roles = jwtAuthenticationToken.getAuthorities()
                            .stream()
                            .map(authority -> authority.getAuthority())
                            .filter(role -> role.startsWith("ROLE_"))
                            .map(role -> role.substring("ROLE_".length()))
                            .distinct()
                            .sorted()
                            .collect(Collectors.joining(","));

                    String correlationId = exchange.getRequest()
                            .getHeaders()
                            .getFirst(GatewayConstants.HEADER_CORRELATION_ID);

                    if (isBlank(correlationId)) {
                        correlationId = UUID.randomUUID().toString();
                    }
                    String signature = sign(
                            userId,
                            roles,
                            correlationId,
                            authorization
                    );
                    log.debug(
                            "Internal auth relay: requestId={}, method={}, path={}, hasAuthorization={}, "
                                    + "principalPresent=true, authorities={}",
                            correlationId,
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getURI().getPath(),
                            !isBlank(authorization),
                            jwtAuthenticationToken.getAuthorities()
                    );
                    ServerHttpRequest mutatedRequest = exchange.getRequest()
                            .mutate()
                            // Xóa header client có thể giả mạo
                            .headers(headers -> {
                                headers.remove(GatewayConstants.HEADER_USER_ID);
                                headers.remove(GatewayConstants.HEADER_USER_ROLES);
                                headers.remove(GatewayConstants.HEADER_SIGNATURE);
                                headers.remove(GatewayConstants.HEADER_CORRELATION_ID);
                            })

                            // Gateway tự set header tin cậy
                            .header(GatewayConstants.HEADER_USER_ID, userId)
                            .header(GatewayConstants.HEADER_USER_ROLES, roles)
                            .header(GatewayConstants.HEADER_CORRELATION_ID, correlationId)
                            .header(GatewayConstants.HEADER_SIGNATURE, signature)

                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .switchIfEmpty(chain.filter(removeInternalHeaders(exchange)));

    }


    private String sign(
            String userId,
            String roles,
            String correlationId,
            String authorization
    ) {
        try {
            String raw = String.join("\n",
                    userId,
                    roles,
                    correlationId,
                    authorization
            );

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    headerSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign internal user context", ex);
        }
    }

    private ServerWebExchange removeInternalHeaders(ServerWebExchange exchange) {
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(GatewayConstants.HEADER_USER_ID);
                    headers.remove(GatewayConstants.HEADER_USER_ROLES);
                    headers.remove(GatewayConstants.HEADER_SIGNATURE);
                })
                .build();

        return exchange.mutate().request(mutatedRequest).build();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String secretFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(headerSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of()
                    .formatHex(hash)
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
        } catch (Exception ex) {
            return "UNAVAILABLE";
        }
    }

    @Override
    public int getOrder() {
        return GatewayConstants.ORDER_JWT_AUTH_FILTER;
    }
}
