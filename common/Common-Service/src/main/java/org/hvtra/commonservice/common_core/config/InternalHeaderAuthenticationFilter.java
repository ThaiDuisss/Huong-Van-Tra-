package org.hvtra.commonservice.common_core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.extern.slf4j.Slf4j;
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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class InternalHeaderAuthenticationFilter extends OncePerRequestFilter {

    final String headerSecret;
    final String applicationName;

    public InternalHeaderAuthenticationFilter(String headerSecret, String applicationName) {
        this.headerSecret = headerSecret;
        this.applicationName = applicationName;
        log.info(
                "Internal auth secret loaded: service={}, property=security.internal.header-secret, length={}, fingerprint={}",
                applicationName,
                headerSecret.length(),
                secretFingerprint()
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userId = value(request.getHeader(HeaderConstants.HEADER_USER_ID));
        String rolesRaw = value(request.getHeader(HeaderConstants.HEADER_USER_ROLES));

        String correlationId = value(request.getHeader(HeaderConstants.HEADER_CORRELATION_ID));
        String signature = value(request.getHeader(HeaderConstants.HEADER_SIGNATURE));
        String authorization = value(request.getHeader(HeaderConstants.HEADER_AUTHOR));
        String permissionsRaw = request.getHeader(HeaderConstants.HEADER_USER_PERMISSIONS);
        String permissionsSignature = value(request.getHeader(HeaderConstants.HEADER_PERMISSIONS_SIGNATURE));

        if (isPublicPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean hasUserId = !isBlank(userId);
        boolean hasRoles = !isBlank(rolesRaw);
        boolean hasRequestId = !isBlank(correlationId);
        boolean hasSignature = !isBlank(signature);
        boolean hasAuthorization = !isBlank(authorization);

        if (!hasUserId || !hasSignature || !hasAuthorization) {
            log.warn(
                    "Internal auth rejected: service={}, reason=missing_headers, method={}, path={}, requestId={}, "
                            + "hasAuthorization={}, hasUserId={}, hasRoles={}, hasSignature={}, principalPresent={}",
                    applicationName,
                    request.getMethod(),
                    request.getRequestURI(),
                    hasRequestId ? correlationId : "missing",
                    hasAuthorization,
                    hasUserId,
                    hasRoles,
                    hasSignature,
                    SecurityContextHolder.getContext().getAuthentication() != null
            );
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing internal authentication headers");
            return;
        }

        String expectedSignature = sign(
                userId,
                rolesRaw,
                correlationId,
                authorization);

        boolean signatureValid = constantTimeEquals(expectedSignature, signature);
        if (!signatureValid) {
            log.warn(
                    "Internal auth rejected: service={}, reason=signature_mismatch, method={}, path={}, requestId={}, "
                            + "hasAuthorization={}, hasUserId={}, hasRoles={}, hasSignature={}, secretLength={}, "
                            + "secretFingerprint={}, signatureValid=false, principalPresent={}",
                    applicationName,
                    request.getMethod(),
                    request.getRequestURI(),
                    hasRequestId ? correlationId : "missing",
                    hasAuthorization,
                    hasUserId,
                    hasRoles,
                    hasSignature,
                    headerSecret.length(),
                    secretFingerprint(),
                    SecurityContextHolder.getContext().getAuthentication() != null
            );
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal authentication signature");
            return;
        }
        // Permission do gateway relay (header có thể vắng với gateway cũ). Có header thì bắt buộc chữ ký riêng hợp lệ.
        Set<String> permissions = null;
        if (permissionsRaw != null) {
            String expectedPermissionsSignature = signPermissions(userId, permissionsRaw, correlationId);
            if (!constantTimeEquals(expectedPermissionsSignature, permissionsSignature)) {
                log.warn(
                        "Internal auth rejected: service={}, reason=permissions_signature_mismatch, method={}, path={}, requestId={}",
                        applicationName,
                        request.getMethod(),
                        request.getRequestURI(),
                        hasRequestId ? correlationId : "missing"
                );
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal permissions signature");
                return;
            }
            permissions = Arrays.stream(permissionsRaw.split(","))
                    .map(String::trim)
                    .filter(permission -> !permission.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
        Set<String> roles = Arrays.stream(rolesRaw.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .collect(Collectors.toSet());

        List<SimpleGrantedAuthority> authorities = new ArrayList<>(roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList());
        if (permissions != null) {
            // Permission giữ nguyên tên (order:create) để phân biệt với ROLE_*.
            permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }

        InternalUserPrincipal principal = new InternalUserPrincipal(
                userId,
                "",
                "",
                roles,
                correlationId,
                permissions);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug(
                "Internal auth accepted: service={}, method={}, path={}, requestId={}, signatureValid=true, "
                        + "principalPresent=true, authorities={}",
                applicationName,
                request.getMethod(),
                request.getRequestURI(),
                hasRequestId ? correlationId : "missing",
                authorities
        );

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

    /** Chữ ký riêng cho header permission: userId, permissions, correlationId (không gồm roles/authorization). */
    private String signPermissions(String userId, String permissions, String correlationId) {
        try {
            String raw = String.join("\n", "permissions", userId, permissions, correlationId);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    headerSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign internal permissions context", ex);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
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


    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
