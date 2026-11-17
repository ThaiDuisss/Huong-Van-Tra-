package org.hvtra.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.core.convert.converter.Converter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Configuration
@Slf4j
@EnableWebFluxSecurity
public class GatewaySecurityConfig {
    private static final String[] PERMIT_ALL_PATHS = {
            "/actuator/**",
            "/api/auth/**",
            "/api/public/**",
            "/api/notifications",
            "/api/notifications/**",
            "/api/payments/webhooks/**"
    };

    @Bean
    SecurityWebFilterChain securityFilterChain(
            ServerHttpSecurity http,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/internal/**", "/api/recommendation/internal/**").denyAll()
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(PERMIT_ALL_PATHS).permitAll()
                        .anyExchange().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                )

                .build();
    }

    @Bean
    public Converter<Jwt, Mono<AbstractAuthenticationToken>>
    jwtAuthenticationConverter() {

        ReactiveJwtAuthenticationConverter converter =
                new ReactiveJwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> Flux.fromIterable(extractAuthorities(jwt))
        );

        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess =
                jwt.getClaimAsMap("realm_access");

        if (realmAccess == null) {
            return List.of();
        }

        Object rolesValue = realmAccess.get("roles");

        if (!(rolesValue instanceof Collection<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(this::normalizeRole)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private String normalizeRole(String role) {
        return role.startsWith("ROLE_")
                ? role
                : "ROLE_" + role;
    }
    /**
     * CORS cho filter chain của Spring Security. PHẢI dùng chung cấu hình với
     * {@code spring.cloud.gateway.server.webflux.globalcors} (CORS_ORIGIN_1/2 trong deploy/.env):
     * Spring Security chạy trước handler mapping của gateway, nên nếu bean này hardcode
     * localhost thì prod sẽ trả 403 body rỗng cho mọi POST/PUT/DELETE (trình duyệt gửi
     * header Origin cho request ghi kể cả khi cùng origin), trong khi GET vẫn chạy.
     * Fallback localhost chỉ dành cho context không có GlobalCorsProperties (test tối giản).
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            ObjectProvider<GlobalCorsProperties> globalCorsProperties
    ) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        GlobalCorsProperties properties = globalCorsProperties.getIfAvailable();
        Map<String, CorsConfiguration> configured =
                properties == null || properties.getCorsConfigurations().isEmpty()
                        ? Map.of("/**", devCorsConfiguration())
                        : properties.getCorsConfigurations();

        configured.forEach((pattern, config) -> {
            log.info("CORS {} -> allowedOriginPatterns={}, allowedOrigins={}",
                    pattern, config.getAllowedOriginPatterns(), config.getAllowedOrigins());
            source.registerCorsConfiguration(pattern, config);
        });

        return source;
    }

    private static CorsConfiguration devCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*"
        ));
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        return config;
    }

}
