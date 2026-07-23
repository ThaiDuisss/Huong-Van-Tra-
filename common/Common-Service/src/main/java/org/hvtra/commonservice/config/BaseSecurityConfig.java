package org.hvtra.commonservice.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


public abstract class BaseSecurityConfig {
    @Value("${security.internal.header-secret}")
    private String headerSecret;

    @Bean
    public InternalHeaderAuthenticationFilter internalHeaderAuthenticationFilter() {
        return new InternalHeaderAuthenticationFilter(
                headerSecret
        );
    }

    protected String[] publicMatchers() {
        return new String[]{
                "/actuator/health",
                "/actuator/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"
        };
    }

    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return securityFilterChain(http, internalHeaderAuthenticationFilter());
    }

    protected SecurityFilterChain securityFilterChain(HttpSecurity http, InternalHeaderAuthenticationFilter internalAuthFilter) throws Exception {

        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicMatchers()).permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(
                        internalAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }
}
