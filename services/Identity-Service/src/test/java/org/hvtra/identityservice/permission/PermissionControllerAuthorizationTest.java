package org.hvtra.identityservice.permission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.identityservice.exception.GlobalException;
import org.hvtra.identityservice.permission.controller.PermissionController;
import org.hvtra.identityservice.permission.dto.PermissionDtos.PermissionResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.RolePermissionsResponse;
import org.hvtra.identityservice.permission.service.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Backend tự kiểm tra permission của người gọi, không tin bất kỳ danh sách quyền nào từ client. */
@ExtendWith(MockitoExtension.class)
class PermissionControllerAuthorizationTest {

    @Mock
    private PermissionService permissionService;

    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new PermissionController(permissionService))
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setValidator(validator)
                .setControllerAdvice(new GlobalException())
                .build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonAdministratorCannotCreatePermission() throws Exception {
        authenticate("u-sale", Set.of("SALE_STAFF"), Set.of("order:create"));

        mvc.perform(post("/api/identity/permissions").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "order:x", "displayName", "X"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(permissionService);
    }

    @Test
    void permissionManagerCanCreatePermission() throws Exception {
        authenticate("u-admin", Set.of("ADMIN"), Set.of("permission:manage"));
        when(permissionService.createPermission(any()))
                .thenReturn(new PermissionResponse("id", "order:x", "X", "", List.of(), false));

        mvc.perform(post("/api/identity/permissions").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "order:x", "displayName", "X"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("order:x"));
    }

    @Test
    void nonAdministratorCannotModifyRolePermissionsEvenWithAdminRoleWhenPermissionsAreRelayed() throws Exception {
        // Gateway mới đã relay permission (rỗng): role ADMIN không còn là fallback.
        authenticate("u-admin", Set.of("ADMIN"), Set.of());

        mvc.perform(put("/api/identity/roles/SALE_STAFF/permissions").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("permissions", List.of("order:view")))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(permissionService);
    }

    @Test
    void legacyGatewayWithoutPermissionHeaderStillAcceptsAdminRole() throws Exception {
        authenticate("u-admin", Set.of("ADMIN"), null);
        when(permissionService.updateRolePermissions(eq("SALE_STAFF"), eq(List.of("order:view")), any()))
                .thenReturn(new RolePermissionsResponse("SALE_STAFF", List.of("order:view")));

        mvc.perform(put("/api/identity/roles/SALE_STAFF/permissions").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("permissions", List.of("order:view")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("order:view"));
    }

    @Test
    void superAdminAlwaysAllowed() throws Exception {
        authenticate("u-root", Set.of("superadmin"), Set.of());
        when(permissionService.listPermissions(null)).thenReturn(List.of());

        mvc.perform(get("/api/identity/permissions")).andExpect(status().isOk());
    }

    @Test
    void userRoleUpdateUsesAuthenticatedPrincipalNotRequestBody() throws Exception {
        authenticate("u-admin", Set.of("ADMIN"), Set.of("user:manage"));
        when(permissionService.updateUserRoles(eq("u-lan"), eq(List.of("MANAGER")), any()))
                .thenReturn(new org.hvtra.identityservice.permission.dto.PermissionDtos.UserRolesResponse(
                        "u-lan", "lan", "Lan", true, List.of("MANAGER"), List.of()));

        mvc.perform(put("/api/identity/users/u-lan/roles").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("roles", List.of("MANAGER"), "callerId", "u-fake"))))
                .andExpect(status().isOk());

        verify(permissionService).updateUserRoles(eq("u-lan"), eq(List.of("MANAGER")),
                org.mockito.ArgumentMatchers.argThat(caller -> "u-admin".equals(caller.userId())));
    }

    @Test
    void bootstrapRequiresAdminRole() throws Exception {
        authenticate("u-manager", Set.of("MANAGER"), Set.of("permission:manage"));

        mvc.perform(post("/api/identity/permissions/bootstrap")).andExpect(status().isForbidden());
        verifyNoInteractions(permissionService);
    }

    private void authenticate(String userId, Set<String> roles, Set<String> permissions) {
        InternalUserPrincipal principal = new InternalUserPrincipal(userId, "", "", roles, "req", permissions);
        List<GrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        if (permissions != null) {
            permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
