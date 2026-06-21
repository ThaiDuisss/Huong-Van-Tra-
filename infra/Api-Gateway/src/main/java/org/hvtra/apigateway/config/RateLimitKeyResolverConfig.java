package org.hvtra.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Configuration
public class RateLimitKeyResolverConfig {
    @Bean
    public KeyResolver keycloakUserOrGuestKeyResolver() {
        return exchange -> exchange.getPrincipal().ofType(JwtAuthenticationToken.class)
                .map(auth -> {
                    Jwt jwt = auth.getToken();
                    String subId = jwt.getClaimAsString("sub");
                    List<String> roles = getRole(jwt);

                    if (roles.contains("admin")) {
                        return "admin:" + subId;
                    }

                    if (roles.contains("staff")) {
                        return "staff:" + subId;
                    }

                    return "user:" + subId;

                }).switchIfEmpty(Mono.fromSupplier(() -> getIp(exchange)));
    }
    public List<String> getRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if(realmAccess == null) {
            return List.of();
        }
        Object role  = realmAccess.get("roles");
        if(role instanceof List<?> roleList) {
            return roleList.stream().map(Object::toString).toList();
        }
        return List.of();
    }
    public String getIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String forwardIp = request.getHeaders().getFirst("X-Forwarded-For");

        if(forwardIp != null && forwardIp.isBlank()) {
            return forwardIp.split(",")[0].trim();
        }
        if(request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
