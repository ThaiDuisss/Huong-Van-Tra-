package org.hvtra.commonservice.common_core.config;

import jakarta.servlet.FilterChain;
import org.hvtra.commonservice.common_core.domain.constants.HeaderConstants;
import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Header permission do gateway relay: phải có chữ ký riêng, thành authority và vào principal. */
class InternalHeaderAuthenticationFilterPermissionsTest {

    private static final String SECRET = "test-internal-secret";
    private static final String USER = "user-1";
    private static final String REQUEST_ID = "req-1";
    private static final String AUTHORIZATION = "Bearer jwt";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestWithoutInternalHeadersIsRejectedWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/pos/checkout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void signedPermissionsBecomeAuthoritiesAndPrincipalPermissions() throws Exception {
        MockHttpServletRequest request = baseRequest("SALE_STAFF");
        request.addHeader(HeaderConstants.HEADER_USER_PERMISSIONS, "order:create,order:view");
        request.addHeader(HeaderConstants.HEADER_PERMISSIONS_SIGNATURE,
                signPermissions("order:create,order:view"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("ROLE_SALE_STAFF", "order:create", "order:view"), authorities);
        InternalUserPrincipal principal = (InternalUserPrincipal) authentication.getPrincipal();
        assertTrue(principal.permissionsKnown());
        assertTrue(principal.hasPermission("order:create"));
        assertTrue(SecurityUtils.hasPermission("order:view"));
        assertFalse(SecurityUtils.hasPermission("order:cancel"));
        // Đã relay permission nên role fallback không còn được tính
        assertThrows(AccessDeniedException.class,
                () -> SecurityUtils.requirePermission("order:cancel", Role.SALE_STAFF));
    }

    @Test
    void tamperedPermissionsHeaderIsRejectedWith401() throws Exception {
        MockHttpServletRequest request = baseRequest("SALE_STAFF");
        request.addHeader(HeaderConstants.HEADER_USER_PERMISSIONS, "permission:manage");
        request.addHeader(HeaderConstants.HEADER_PERMISSIONS_SIGNATURE, signPermissions("order:view"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter().doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void legacyGatewayWithoutPermissionHeaderFallsBackToRoles() throws Exception {
        MockHttpServletRequest request = baseRequest("SALE_STAFF");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, mock(FilterChain.class));

        InternalUserPrincipal principal =
                (InternalUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertFalse(principal.permissionsKnown());
        SecurityUtils.requirePermission("order:create", Role.SALE_STAFF);
        assertThrows(AccessDeniedException.class,
                () -> SecurityUtils.requirePermission("order:create", Role.SUPER_ADMIN));
    }

    @Test
    void emptyPermissionListFromGatewayIsStrict() throws Exception {
        MockHttpServletRequest request = baseRequest("ADMIN");
        request.addHeader(HeaderConstants.HEADER_USER_PERMISSIONS, "");
        request.addHeader(HeaderConstants.HEADER_PERMISSIONS_SIGNATURE, signPermissions(""));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, mock(FilterChain.class));

        assertEquals(200, response.getStatus());
        InternalUserPrincipal principal =
                (InternalUserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertTrue(principal.permissionsKnown());
        assertTrue(principal.permissions().isEmpty());
        assertThrows(AccessDeniedException.class,
                () -> SecurityUtils.requirePermission("permission:manage", Role.SUPER_ADMIN));
    }

    private InternalHeaderAuthenticationFilter filter() {
        return new InternalHeaderAuthenticationFilter(SECRET, "order-service");
    }

    private MockHttpServletRequest baseRequest(String roles) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/pos/checkout");
        request.addHeader(HeaderConstants.HEADER_USER_ID, USER);
        request.addHeader(HeaderConstants.HEADER_USER_ROLES, roles);
        request.addHeader(HeaderConstants.HEADER_CORRELATION_ID, REQUEST_ID);
        request.addHeader(HeaderConstants.HEADER_AUTHOR, AUTHORIZATION);
        request.addHeader(HeaderConstants.HEADER_SIGNATURE,
                hmac(String.join("\n", USER, roles, REQUEST_ID, AUTHORIZATION)));
        return request;
    }

    private String signPermissions(String permissions) throws Exception {
        return hmac(String.join("\n", "permissions", USER, permissions, REQUEST_ID));
    }

    private static String hmac(String raw) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
