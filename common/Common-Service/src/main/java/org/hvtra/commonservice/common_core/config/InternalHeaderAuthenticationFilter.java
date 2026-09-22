package org.hvtra.commonservice.common_core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hvtra.commonservice.common_core.domain.constants.HeaderConstants;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class InternalHeaderAuthenticationFilter extends OncePerRequestFilter {

    final String headerSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userId = value(request.getHeader(HeaderConstants.HEADER_USER_ID));
        String rolesRaw = value(request.getHeader(HeaderConstants.HEADER_USER_ROLES));

        String correlationId = value(request.getHeader(HeaderConstants.HEADER_CORRELATION_ID));
        String signature = value(request.getHeader(HeaderConstants.HEADER_SIGNATURE));
        String authorization = value(request.getHeader(HeaderConstants.HEADER_AUTHOR));

        if (isPublicPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isBlank(userId) || isBlank(signature) || isBlank(authorization)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing internal authentication headers");
            return;
        }

        String expectedSignature = sign(
                userId,
                rolesRaw,
                correlationId,
                authorization);

        if (!constantTimeEquals(expectedSignature, signature)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal authentication signature");
            return;
        }
        Set<String> roles = Arrays.stream(rolesRaw.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .collect(Collectors.toSet());

        var authorities = roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();

        InternalUserPrincipal principal = new InternalUserPrincipal(
                userId,
                "",
                "",
                roles,
                correlationId);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        return result == 0;
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/actuator")
                || path.contains("/v3/api-docs")
                || path.contains("/swagger-ui")
                || path.contains("/api/inventory/reservations/release-order")
                || path.matches("^/api/[^/]+/public(/.*)?$")
                || path.matches("^/api/payments/webhooks(/.*)?$");
    }

    private String sign(
            String userId,
            String roles,
            String correlationId,
            String authorization) {
        try {
            String raw = String.join("\n",
                    userId,
                    roles,
                    correlationId,
                    authorization);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    headerSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));

            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign internal user context", ex);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }


    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
