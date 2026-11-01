package org.hvtra.identityservice.permission;

import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.identityservice.account.client.KeycloakAdminClient;
import org.hvtra.identityservice.account.client.KeycloakAdminException;
import org.hvtra.identityservice.account.client.KeycloakClient;
import org.hvtra.identityservice.account.client.KeycloakRole;
import org.hvtra.identityservice.account.client.KeycloakUser;
import org.hvtra.identityservice.account.config.KeycloakAdminProperties;
import org.hvtra.identityservice.permission.dto.PermissionDtos.BootstrapResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.CreatePermissionRequest;
import org.hvtra.identityservice.permission.dto.PermissionDtos.MyPermissionsResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.PermissionResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.RolePermissionsResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.UserRolesResponse;
import org.hvtra.identityservice.permission.model.PermissionCatalog;
import org.hvtra.identityservice.permission.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionServiceTest {

    private static final String CLIENT_UUID = "client-uuid";
    private static final KeycloakRole QC = new KeycloakRole("r-qc", "QC");
    private static final KeycloakRole MANAGER = new KeycloakRole("r-manager", "manager");
    private static final KeycloakRole SALE_STAFF = new KeycloakRole("r-sale", "SALE_STAFF");
    private static final KeycloakRole SUPER_ADMIN = new KeycloakRole("r-super", "superadmin");
    private static final KeycloakRole ORDER_VIEW = permission("p-view", "order:view", "Xem đơn", "Xem đơn hàng");
    private static final KeycloakRole ORDER_CREATE = permission("p-create", "order:create", "Tạo đơn", "Bán hàng");
    private static final KeycloakRole PERMISSION_MANAGE = permission("p-manage", "permission:manage", "Quản lý quyền", "");

    @Mock
    private KeycloakAdminClient keycloak;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(keycloak,
                new KeycloakAdminProperties("http://kc", "hvt", "identity-admin", "secret", "hvtra-pos"));
        when(keycloak.findClientByClientId("hvtra-pos")).thenReturn(new KeycloakClient(CLIENT_UUID, "hvtra-pos"));
        when(keycloak.listRealmRoles()).thenReturn(List.of(QC, MANAGER, SALE_STAFF, SUPER_ADMIN,
                new KeycloakRole("r-default", "default-roles-hvt")));
        when(keycloak.listClientRoles(CLIENT_UUID)).thenReturn(List.of(ORDER_VIEW, ORDER_CREATE, PERMISSION_MANAGE));
        when(keycloak.getRealmRoleClientComposites(any(), eq(CLIENT_UUID))).thenReturn(List.of());
        when(keycloak.getRealmRoleClientComposites("SALE_STAFF", CLIENT_UUID)).thenReturn(List.of(ORDER_VIEW, ORDER_CREATE));
        when(keycloak.getRealmRoleClientComposites("QC", CLIENT_UUID)).thenReturn(List.of(PERMISSION_MANAGE));
    }

    @Test
    void listsPermissionsWithMetadataAndAssignedRoles() {
        List<PermissionResponse> permissions = service.listPermissions(null);

        assertThat(permissions).extracting(PermissionResponse::name)
                .containsExactly("order:create", "order:view", "permission:manage");
        PermissionResponse view = permissions.get(1);
        assertThat(view.displayName()).isEqualTo("Xem đơn");
        assertThat(view.description()).isEqualTo("Xem đơn hàng");
        assertThat(view.assignedRoles()).containsExactly("SALE_STAFF");
        assertThat(view.builtIn()).isTrue();
        assertThat(service.listPermissions("quản lý")).extracting(PermissionResponse::name)
                .containsExactly("permission:manage");
    }

    @Test
    void createsPermissionAsClientRoleWithDisplayNameAttribute() {
        when(keycloak.getClientRole(CLIENT_UUID, "inventory:count"))
                .thenThrow(new KeycloakAdminException(404, "not found"))
                .thenReturn(permission("p-new", "inventory:count", "Kiểm kê", "Đếm tồn"));

        PermissionResponse created = service.createPermission(
                new CreatePermissionRequest(" Inventory:Count ", "Kiểm kê", "Đếm tồn"));

        ArgumentCaptor<KeycloakRole> captor = ArgumentCaptor.forClass(KeycloakRole.class);
        verify(keycloak).createClientRole(eq(CLIENT_UUID), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("inventory:count");
        assertThat(captor.getValue().description()).isEqualTo("Đếm tồn");
        assertThat(captor.getValue().attributes()).containsEntry("displayName", List.of("Kiểm kê"));
        assertThat(created.name()).isEqualTo("inventory:count");
        assertThat(created.builtIn()).isFalse();
    }

    @Test
    void rejectsInvalidPermissionNameAndDuplicate() {
        assertThatThrownBy(() -> service.createPermission(new CreatePermissionRequest("Order Create", "x", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PERMISSION_NAME_INVALID");

        when(keycloak.getClientRole(CLIENT_UUID, "order:view")).thenReturn(ORDER_VIEW);
        assertThatThrownBy(() -> service.createPermission(new CreatePermissionRequest("order:view", "x", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(keycloak, never()).createClientRole(any(), any());
    }

    @Test
    void refusesToDeletePermissionStillAssignedToARole() {
        when(keycloak.getClientRole(CLIENT_UUID, "order:view")).thenReturn(ORDER_VIEW);

        assertThatThrownBy(() -> service.deletePermission("order:view"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PERMISSION_IN_USE");
        verify(keycloak, never()).deleteClientRole(any(), any());
    }

    @Test
    void deletesUnassignedPermissionWithoutDirectUsers() {
        KeycloakRole orphan = permission("p-orphan", "report:export", "Xuất", "");
        when(keycloak.getClientRole(CLIENT_UUID, "report:export")).thenReturn(orphan);
        when(keycloak.listUsersInClientRole(CLIENT_UUID, "report:export")).thenReturn(List.of());

        service.deletePermission("report:export");

        verify(keycloak).deleteClientRole(CLIENT_UUID, "report:export");
    }

    @Test
    void updateRolePermissionsAddsAndRemovesOnlyTheDifference() {
        InternalUserPrincipal admin = principal("u-admin", Set.of("QC"));

        RolePermissionsResponse response = service.updateRolePermissions(
                "sale_staff", List.of("order:view", "permission:manage"), admin);

        verify(keycloak).addRealmRoleComposites("SALE_STAFF", List.of(new KeycloakRole("p-manage", "permission:manage")));
        verify(keycloak).removeRealmRoleComposites("SALE_STAFF", List.of(new KeycloakRole("p-create", "order:create")));
        assertThat(response.role()).isEqualTo("SALE_STAFF");
    }

    @Test
    void updateRolePermissionsRejectsUnknownPermissionBeforeTouchingKeycloak() {
        assertThatThrownBy(() -> service.updateRolePermissions("SALE_STAFF", List.of("order:ghost"),
                principal("u-admin", Set.of("QC"))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("PERMISSION_NOT_FOUND");
        verify(keycloak, never()).addRealmRoleComposites(any(), anyList());
    }

    @Test
    void callerCannotChangePermissionsOfTheirOwnRoleUnlessSuperAdmin() {
        assertThatThrownBy(() -> service.updateRolePermissions("QC", List.of("permission:manage"),
                principal("u-admin", Set.of("QC"))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ROLE_SELF_EDIT");

        service.updateRolePermissions("QC", List.of("permission:manage", "order:view"),
                principal("u-root", Set.of("superadmin")));
        verify(keycloak).addRealmRoleComposites("QC", List.of(new KeycloakRole("p-view", "order:view")));
    }

    @Test
    void superAdminRoleIsNeverManageable() {
        assertThatThrownBy(() -> service.getRolePermissions("SUPER_ADMIN"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ROLE_NOT_ALLOWED");
    }

    @Test
    void updateUserRolesComputesDifferenceAndKeepsUnrelatedRoles() {
        KeycloakUser lan = user("u-lan", "lan");
        when(keycloak.getUser("u-lan")).thenReturn(lan);
        when(keycloak.getUserRealmRoles("u-lan")).thenReturn(List.of(SALE_STAFF, new KeycloakRole("r-default", "default-roles-hvt")));
        when(keycloak.getUserEffectiveClientRoles("u-lan", CLIENT_UUID)).thenReturn(List.of(ORDER_VIEW));

        UserRolesResponse response = service.updateUserRoles("u-lan", List.of("MANAGER"),
                principal("u-admin", Set.of("QC")));

        verify(keycloak).addUserRealmRoles("u-lan", List.of(new KeycloakRole("r-manager", "manager")));
        verify(keycloak).removeUserRealmRoles("u-lan", List.of(new KeycloakRole("r-sale", "SALE_STAFF")));
        assertThat(response.roles()).containsExactly("MANAGER");
        assertThat(response.effectivePermissions()).containsExactly("order:view");
    }

    @Test
    void userCannotChangeTheirOwnRolesAndCannotAssignSuperAdmin() {
        assertThatThrownBy(() -> service.updateUserRoles("u-admin", List.of("QC"), principal("u-admin", Set.of("QC"))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("USER_SELF_ROLE_CHANGE");
        assertThatThrownBy(() -> service.updateUserRoles("u-lan", List.of("SUPER_ADMIN"), principal("u-admin", Set.of("QC"))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ROLE_NOT_ALLOWED");
        verify(keycloak, never()).addUserRealmRoles(any(), anyList());
    }

    @Test
    void cannotChangeRolesOfSuperAdminAccount() {
        when(keycloak.getUser("u-root")).thenReturn(user("u-root", "root"));
        when(keycloak.getUserRealmRoles("u-root")).thenReturn(List.of(SUPER_ADMIN, QC));

        assertThatThrownBy(() -> service.updateUserRoles("u-root", List.of("MANAGER"), principal("u-admin", Set.of("QC"))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("ACCOUNT_PROTECTED");
    }

    @Test
    void missingUserTranslatesToNotFound() {
        when(keycloak.getUser("ghost")).thenThrow(new KeycloakAdminException(404, "User not found"));

        assertThatThrownBy(() -> service.getUserRoles("ghost"))
                .hasMessageContaining("Không tìm thấy");
    }

    @Test
    void keycloakAuthorizationErrorBecomesBadGatewayBusinessException() {
        when(keycloak.listClientRoles(CLIENT_UUID)).thenThrow(new KeycloakAdminException(403, "forbidden"));

        assertThatThrownBy(() -> service.listPermissions(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void myPermissionsFallsBackToTokenClaimsWhenKeycloakIsDown() {
        when(keycloak.getUserEffectiveClientRoles("u-lan", CLIENT_UUID)).thenReturn(List.of(ORDER_CREATE, ORDER_VIEW));
        MyPermissionsResponse fresh = service.myPermissions(principal("u-lan", Set.of("SALE_STAFF"), Set.of("order:view")));
        assertThat(fresh.source()).isEqualTo("keycloak");
        assertThat(fresh.permissions()).containsExactly("order:create", "order:view");

        when(keycloak.getUserEffectiveClientRoles("u-lan", CLIENT_UUID))
                .thenThrow(new KeycloakAdminException(KeycloakAdminException.STATUS_UNREACHABLE, "down"));
        MyPermissionsResponse fallback = service.myPermissions(principal("u-lan", Set.of("SALE_STAFF"), Set.of("order:view")));
        assertThat(fallback.source()).isEqualTo("token");
        assertThat(fallback.permissions()).containsExactly("order:view");
    }

    @Test
    void bootstrapCreatesMissingPermissionsAndOnlyAddsMissingDefaults() {
        when(keycloak.listClientRoles(CLIENT_UUID))
                .thenReturn(List.of(ORDER_VIEW, ORDER_CREATE, PERMISSION_MANAGE))
                .thenReturn(PermissionCatalog.DEFAULT_PERMISSIONS.stream()
                        .map(p -> permission("p-" + p.name(), p.name(), p.displayName(), p.description()))
                        .toList());

        BootstrapResponse response = service.bootstrap();

        assertThat(response.createdPermissions())
                .hasSize(PermissionCatalog.DEFAULT_PERMISSIONS.size() - 3)
                .doesNotContain("order:view", "order:create", "permission:manage");
        // SALE_STAFF đã có order:view/order:create -> chỉ thêm phần còn thiếu, không gỡ gì
        ArgumentCaptor<List<KeycloakRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(keycloak).addRealmRoleComposites(eq("SALE_STAFF"), captor.capture());
        assertThat(captor.getValue()).extracting(KeycloakRole::name)
                .containsExactly("customer:view", "inventory:view", "order:return", "product:view");
        verify(keycloak, never()).removeRealmRoleComposites(any(), anyList());
        assertThat(response.updatedRoles()).contains("QC", "SALE_STAFF", "MANAGER");
    }

    private static KeycloakRole permission(String id, String name, String displayName, String description) {
        return new KeycloakRole(id, name, description, false, true, Map.of("displayName", List.of(displayName)));
    }

    private static InternalUserPrincipal principal(String userId, Set<String> roles) {
        return new InternalUserPrincipal(userId, "", "", roles, "req");
    }

    private static InternalUserPrincipal principal(String userId, Set<String> roles, Set<String> permissions) {
        return new InternalUserPrincipal(userId, "", "", roles, "req", permissions);
    }

    private static KeycloakUser user(String id, String username) {
        KeycloakUser user = new KeycloakUser();
        user.setId(id);
        user.setUsername(username);
        user.setFirstName("A");
        user.setLastName("B");
        return user;
    }
}
