package org.hvtra.commonservice.common_core.config;


import jakarta.servlet.http.HttpServletResponse;
import org.hvtra.commonservice.common_core.exception.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


public abstract class BaseSecurityConfig {
    @Value("${security.internal.header-secret}")
    private String headerSecret;

    @Value("${spring.application.name:unknown-service}")
    private String applicationName;

    @Bean
    public InternalHeaderAuthenticationFilter internalHeaderAuthenticationFilter() {
        return new InternalHeaderAuthenticationFilter(
                headerSecret,
                applicationName
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

    /**
     * Phân quyền theo URL tập trung của từng service (vd. {@code .hasAnyRole("ADMIN", "MANAGER")}), khai báo trước
     * luật mặc định {@code anyRequest().permitAll()}. Mặc định không thêm luật nào.
     */
    protected void authorizeRequests(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
    }

    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return securityFilterChain(http, internalHeaderAuthenticationFilter());
    }

    protected SecurityFilterChain securityFilterChain(HttpSecurity http, InternalHeaderAuthenticationFilter internalAuthFilter) throws Exception {

        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(publicMatchers()).permitAll();
                    authorizeRequests(auth);
                    auth.anyRequest().permitAll();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa đăng nhập"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này"))
                )
                .addFilterBefore(
                        internalAuthFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .build();
    }

    /** Body lỗi cùng dạng {@link ErrorResponse} với GlobalExceptionHandler để FE đọc message thống nhất. */
    private static void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"status\":" + status.value() + "}");
    }
}
