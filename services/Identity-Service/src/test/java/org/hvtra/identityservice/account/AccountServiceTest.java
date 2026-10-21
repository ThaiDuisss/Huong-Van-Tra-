package org.hvtra.identityservice.account;

import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.identityservice.account.client.KeycloakAdminClient;
import org.hvtra.identityservice.account.client.KeycloakAdminException;
import org.hvtra.identityservice.account.client.KeycloakRole;
import org.hvtra.identityservice.account.client.KeycloakUser;
import org.hvtra.identityservice.account.dto.AccountResponse;
import org.hvtra.identityservice.account.dto.AccountSearchRequest;
import org.hvtra.identityservice.account.dto.CreateAccountRequest;
import org.hvtra.identityservice.account.dto.UpdateAccountRequest;
import org.hvtra.identityservice.account.service.AccountService;
import org.hvtra.identityservice.actionlog.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    // Realm cố ý dùng tên role "lệch chuẩn" để kiểm tra việc chuẩn hóa: chữ thường, alias, không gạch dưới.
    private static final KeycloakRole SUPER_ADMIN = new KeycloakRole("r-super", "superadmin");
    private static final KeycloakRole ADMIN = new KeycloakRole("r-admin", "ADMIN");
    private static final KeycloakRole MANAGER = new KeycloakRole("r-manager", "manager");
    private static final KeycloakRole SALE_STAFF = new KeycloakRole("r-sale", "SALE_STAFF");
    private static final KeycloakRole WAREHOUSE = new KeycloakRole("r-warehouse", "WAREHOUSE_STAFF");
    private static final KeycloakRole CUSTOMER = new KeycloakRole("r-customer", "CUSTOMER");
    private static final KeycloakRole DEFAULT = new KeycloakRole("r-default", "default-roles-hvt");

    @Mock
    private KeycloakAdminClient keycloak;

    @InjectMocks
    private AccountService service;

    private KeycloakUser root;
    private KeycloakUser lan;
    private KeycloakUser minh;
    private KeycloakUser binh;

    @BeforeEach
    void setUp() {
        root = user("u-root", "root", "root@hvt.vn", "Admin", "Super", true);
        lan = user("u-lan", "lan.nguyen", "lan@hvt.vn", "Lan", "Nguyễn Thị", true);
        minh = user("u-minh", "minh.tran", "minh@hvt.vn", "Minh", "Trần", false);
        binh = user("u-binh", "binh.le", "binh@hvt.vn", "Bình", "Lê", true);

        when(keycloak.listRealmRoles())
                .thenReturn(List.of(SUPER_ADMIN, ADMIN, MANAGER, SALE_STAFF, WAREHOUSE, CUSTOMER, DEFAULT));
        when(keycloak.listUsersInRole(anyString())).thenReturn(List.of());
        when(keycloak.listUsersInRole("superadmin")).thenReturn(List.of(root));
        when(keycloak.listUsersInRole("ADMIN")).thenReturn(List.of(lan));
        when(keycloak.listUsersInRole("SALE_STAFF")).thenReturn(List.of(lan, minh));
        when(keycloak.listUsersInRole("WAREHOUSE_STAFF")).thenReturn(List.of(binh));

        when(keycloak.getUser("u-root")).thenReturn(root);
        when(keycloak.getUser("u-lan")).thenReturn(lan);
        when(keycloak.getUser("u-minh")).thenReturn(minh);
        when(keycloak.getUserRealmRoles("u-root")).thenReturn(List.of(SUPER_ADMIN, DEFAULT));
        when(keycloak.getUserRealmRoles("u-lan")).thenReturn(List.of(ADMIN, SALE_STAFF, DEFAULT));
        when(keycloak.getUserRealmRoles("u-minh")).thenReturn(List.of(SALE_STAFF, DEFAULT));
    }

    // ===== danh sách / tìm kiếm / lọc =====

    @Test
    void search_mergesRoleMembers_sortsByUsername_andNeverScansCustomerRole() {
        PageResponse<AccountResponse> page = service.search(new AccountSearchRequest());

        assertThat(page.content()).extracting(AccountResponse::username)
                .containsExactly("binh.le", "lan.nguyen", "minh.tran", "root");
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.totalPages()).isEqualTo(1);

        AccountResponse lanAccount = page.content().get(1);
        assertThat(lanAccount.roles()).containsExactly("ADMIN", "SALE_STAFF");
        assertThat(lanAccount.fullName()).isEqualTo("Nguyễn Thị Lan");
        assertThat(lanAccount.editable()).isTrue();

        // Alias WAREHOUSE_STAFF được hiển thị thành INVENTORY_STAFF
        assertThat(page.content().get(0).roles()).containsExactly("INVENTORY_STAFF");
        // SUPER_ADMIN chỉ để xem
        assertThat(page.content().get(3).roles()).containsExactly("SUPER_ADMIN");
        assertThat(page.content().get(3).editable()).isFalse();

        verify(keycloak, never()).listUsersInRole("CUSTOMER");
        verify(keycloak, never()).listUsersInRole("default-roles-hvt");
    }

    @Test
    void search_filtersByKeywordRoleAndStatus() {
        AccountSearchRequest byName = new AccountSearchRequest();
        byName.setKeyword("  nguyễn thị LAN ");
        assertThat(service.search(byName).content()).extracting(AccountResponse::id).containsExactly("u-lan");

        AccountSearchRequest byEmail = new AccountSearchRequest();
        byEmail.setKeyword("minh@");
        assertThat(service.search(byEmail).content()).extracting(AccountResponse::id).containsExactly("u-minh");

        AccountSearchRequest byRole = new AccountSearchRequest();
        byRole.setRole("ROLE_SALE_STAFF");
        assertThat(service.search(byRole).content()).extracting(AccountResponse::id)
                .containsExactly("u-lan", "u-minh");

        AccountSearchRequest disabled = new AccountSearchRequest();
        disabled.setEnabled(false);
        assertThat(service.search(disabled).content()).extracting(AccountResponse::id).containsExactly("u-minh");

        AccountSearchRequest roleAndStatus = new AccountSearchRequest();
        roleAndStatus.setRole("SALE_STAFF");
        roleAndStatus.setEnabled(true);
        assertThat(service.search(roleAndStatus).content()).extracting(AccountResponse::id).containsExactly("u-lan");
    }

    @Test
    void search_pagesInMemory() {
        AccountSearchRequest request = new AccountSearchRequest();
        request.setPage(1);
        request.setSize(3);

        PageResponse<AccountResponse> page = service.search(request);

        assertThat(page.content()).extracting(AccountResponse::username).containsExactly("root");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(3);
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.totalPages()).isEqualTo(2);

        request.setPage(5);
        assertThat(service.search(request).content()).isEmpty();
    }

    @Test
    void search_rejectsFilteringBySuperAdminOrCustomer() {
        AccountSearchRequest request = new AccountSearchRequest();
        request.setRole("SUPER_ADMIN");
        assertThatThrownBy(() -> service.search(request)).isInstanceOf(BusinessException.class);

        request.setRole("CUSTOMER");
        assertThatThrownBy(() -> service.search(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void assignableRoles_onlyListsStaffRolesExistingInRealm() {
        assertThat(service.assignableRoles())
                .containsExactly("ADMIN", "MANAGER", "INVENTORY_STAFF", "SALE_STAFF");
    }

    // ===== chi tiết =====

    @Test
    void get_returnsAccountWithCanonicalRoles() {
        AccountResponse account = service.get("u-lan");

        assertThat(account.username()).isEqualTo("lan.nguyen");
        assertThat(account.roles()).containsExactly("ADMIN", "SALE_STAFF");
        assertThat(account.enabled()).isTrue();
    }

    @Test
    void get_hidesCustomerOnlyAccounts() {
        when(keycloak.getUser("u-customer")).thenReturn(user("u-customer", "khach", "k@x.vn", "A", "B", true));
        when(keycloak.getUserRealmRoles("u-customer")).thenReturn(List.of(CUSTOMER, DEFAULT));

        assertThatThrownBy(() -> service.get("u-customer")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_mapsKeycloak404ToNotFound() {
        when(keycloak.getUser("missing")).thenThrow(new KeycloakAdminException(404, "User not found"));

        assertThatThrownBy(() -> service.get("missing")).isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== tạo =====

    @Test
    void create_sendsTemporaryPasswordAndAssignsResolvedRealmRoles() {
        when(keycloak.createUser(any())).thenReturn("u-new");
        when(keycloak.getUser("u-new")).thenReturn(user("u-new", "hoa.pham", "hoa@hvt.vn", "Hoa", "Phạm", true));

        AccountResponse created = service.create(new CreateAccountRequest(
                " hoa.pham ", "Hoa@HVT.vn", "Hoa", "Phạm", "Init@12345", List.of("MANAGER", "ROLE_INVENTORY_STAFF")));

        ArgumentCaptor<KeycloakUser> userCaptor = ArgumentCaptor.forClass(KeycloakUser.class);
        verify(keycloak).createUser(userCaptor.capture());
        KeycloakUser sent = userCaptor.getValue();
        assertThat(sent.getUsername()).isEqualTo("hoa.pham");
        assertThat(sent.getEmail()).isEqualTo("hoa@hvt.vn");
        assertThat(sent.getEnabled()).isTrue();
        assertThat(sent.getCredentials()).singleElement().satisfies(credential -> {
            assertThat(credential.type()).isEqualTo("password");
            assertThat(credential.value()).isEqualTo("Init@12345");
            assertThat(credential.temporary()).isTrue();
        });

        // Gán đúng realm role thật trong Keycloak ("manager", alias "WAREHOUSE_STAFF")
        verify(keycloak).addUserRealmRoles("u-new", List.of(MANAGER, WAREHOUSE));
        assertThat(created.id()).isEqualTo("u-new");
        assertThat(created.roles()).containsExactly("MANAGER", "INVENTORY_STAFF");
    }

    @Test
    void create_rejectsSuperAdminAndCustomerRoles_withoutCallingKeycloak() {
        for (String forbidden : List.of("SUPER_ADMIN", "superadmin", "ROLE_SUPER_ADMIN", "CUSTOMER", "UNKNOWN")) {
            assertThatThrownBy(() -> service.create(new CreateAccountRequest(
                    "x.user", "x@hvt.vn", "X", "Y", "Init@12345", List.of("ADMIN", forbidden))))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getCode())
                    .isEqualTo("ACCOUNT_ROLE_NOT_ALLOWED");
        }
        verifyNoInteractions(keycloak);
    }

    @Test
    void create_failsBeforeCreatingUser_whenRoleMissingInRealm() {
        assertThatThrownBy(() -> service.create(new CreateAccountRequest(
                "x.user", "x@hvt.vn", "X", "Y", "Init@12345", List.of("ACCOUNTANT"))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ACCOUNT_ROLE_NOT_FOUND");

        verify(keycloak, never()).createUser(any());
    }

    @Test
    void create_mapsKeycloak409ToConflict() {
        when(keycloak.createUser(any()))
                .thenThrow(new KeycloakAdminException(409, "User exists with same email"));

        assertThatThrownBy(() -> service.create(new CreateAccountRequest(
                "x.user", "lan@hvt.vn", "X", "Y", "Init@12345", List.of("ADMIN"))))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("ACCOUNT_ALREADY_EXISTS");
                    assertThat(ex.getMessage()).contains("Email");
                });
    }

    @Test
    void create_rollsBackUser_whenRoleAssignmentFails() {
        when(keycloak.createUser(any())).thenReturn("u-new");
        doThrow(new KeycloakAdminException(403, "forbidden"))
                .when(keycloak).addUserRealmRoles(eq("u-new"), anyList());

        assertThatThrownBy(() -> service.create(new CreateAccountRequest(
                "x.user", "x@hvt.vn", "X", "Y", "Init@12345", List.of("ADMIN"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));

        verify(keycloak).deleteUser("u-new");
    }

    @Test
    void createRequest_toStringDoesNotLeakPassword() {
        CreateAccountRequest request = new CreateAccountRequest(
                "x.user", "x@hvt.vn", "X", "Y", "Init@12345", List.of("ADMIN"));

        assertThat(request.toString()).doesNotContain("Init@12345");
        assertThat(KeycloakUser.Credential.temporaryPassword("Init@12345").toString()).doesNotContain("Init@12345");
    }

    // ===== sửa =====

    @Test
    void update_changesProfileAndOnlyDiffsStaffRoles() {
        lan.putOther("attributes", java.util.Map.of("phone", List.of("0901")));

        AccountResponse updated = service.update("u-lan", new UpdateAccountRequest(
                "Lan.New@hvt.vn", "Lan", "Nguyễn", List.of("ADMIN", "MANAGER")));

        ArgumentCaptor<KeycloakUser> userCaptor = ArgumentCaptor.forClass(KeycloakUser.class);
        verify(keycloak).updateUser(eq("u-lan"), userCaptor.capture());
        KeycloakUser sent = userCaptor.getValue();
        assertThat(sent.getEmail()).isEqualTo("lan.new@hvt.vn");
        assertThat(sent.getLastName()).isEqualTo("Nguyễn");
        assertThat(sent.getUsername()).isEqualTo("lan.nguyen");
        // Thuộc tính khác của user phải được gửi lại nguyên vẹn
        assertThat(sent.getOther()).containsKey("attributes");

        verify(keycloak).addUserRealmRoles("u-lan", List.of(MANAGER));
        // ADMIN giữ nguyên, SALE_STAFF bị gỡ, default-roles không bị đụng tới
        verify(keycloak).removeUserRealmRoles("u-lan", List.of(SALE_STAFF));
        assertThat(updated.roles()).containsExactly("ADMIN", "MANAGER");
    }

    @Test
    void update_rejectsSuperAdminAccount() {
        assertThatThrownBy(() -> service.update("u-root", new UpdateAccountRequest(
                "root@hvt.vn", "Admin", "Super", List.of("ADMIN"))))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("ACCOUNT_PROTECTED");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verify(keycloak, never()).updateUser(anyString(), any());
        verify(keycloak, never()).addUserRealmRoles(anyString(), anyList());
        verify(keycloak, never()).removeUserRealmRoles(anyString(), anyList());
    }

    @Test
    void update_rejectsAssigningSuperAdmin() {
        assertThatThrownBy(() -> service.update("u-lan", new UpdateAccountRequest(
                "lan@hvt.vn", "Lan", "Nguyễn", List.of("SUPER_ADMIN"))))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(keycloak);
    }

    // ===== khóa / mở khóa =====

    @Test
    void changeStatus_disablesAccountAndEndsSessions() {
        AccountResponse result = service.changeStatus("u-lan", false, "u-root");

        ArgumentCaptor<KeycloakUser> userCaptor = ArgumentCaptor.forClass(KeycloakUser.class);
        verify(keycloak).updateUser(eq("u-lan"), userCaptor.capture());
        assertThat(userCaptor.getValue().getEnabled()).isFalse();
        verify(keycloak).logoutUser("u-lan");
        assertThat(result.enabled()).isFalse();
    }

    @Test
    void changeStatus_enablesAccountWithoutLogout() {
        AccountResponse result = service.changeStatus("u-minh", true, "u-root");

        assertThat(result.enabled()).isTrue();
        verify(keycloak, never()).logoutUser(anyString());
    }

    @Test
    void changeStatus_stillSucceeds_whenLogoutFails() {
        doThrow(new KeycloakAdminException(500, "boom")).when(keycloak).logoutUser("u-lan");

        assertThat(service.changeStatus("u-lan", false, "u-root").enabled()).isFalse();
    }

    @Test
    void changeStatus_rejectsDisablingOwnAccount() {
        assertThatThrownBy(() -> service.changeStatus("u-lan", false, "u-lan"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_SELF_DISABLE"));

        verifyNoInteractions(keycloak);
    }

    @Test
    void changeStatus_rejectsSuperAdminAccount() {
        assertThatThrownBy(() -> service.changeStatus("u-root", false, "u-other"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_PROTECTED"));

        verify(keycloak, never()).updateUser(anyString(), any());
    }

    // ===== lỗi hạ tầng =====

    // ===== đặt lại mật khẩu =====

    @Test
    void resetPassword_setsTemporaryPasswordAndEndsSessions() {
        AccountResponse result = service.resetPassword("u-lan", "Moi@12345");

        ArgumentCaptor<KeycloakUser.Credential> credential = ArgumentCaptor.forClass(KeycloakUser.Credential.class);
        verify(keycloak).resetPassword(eq("u-lan"), credential.capture());
        assertThat(credential.getValue().type()).isEqualTo("password");
        assertThat(credential.getValue().value()).isEqualTo("Moi@12345");
        assertThat(credential.getValue().temporary()).isTrue();
        verify(keycloak).logoutUser("u-lan");
        assertThat(result.id()).isEqualTo("u-lan");
    }

    @Test
    void resetPassword_stillSucceeds_whenLogoutFails() {
        doThrow(new KeycloakAdminException(500, "boom")).when(keycloak).logoutUser("u-lan");

        assertThat(service.resetPassword("u-lan", "Moi@12345").id()).isEqualTo("u-lan");
        verify(keycloak).resetPassword(eq("u-lan"), any());
    }

    @Test
    void resetPassword_rejectsSuperAdminAccount() {
        assertThatThrownBy(() -> service.resetPassword("u-root", "Moi@12345"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_PROTECTED"));

        verify(keycloak, never()).resetPassword(anyString(), any());
    }

    @Test
    void resetPasswordRequest_toStringDoesNotLeakPassword() {
        assertThat(new org.hvtra.identityservice.account.dto.ResetPasswordRequest("Moi@12345").toString())
                .doesNotContain("Moi@12345");
    }

    @Test
    void keycloakNotConfiguredOrUnreachable_mapsTo503() {
        when(keycloak.listRealmRoles()).thenThrow(new KeycloakAdminException(
                KeycloakAdminException.STATUS_NOT_CONFIGURED, "missing config"));
        assertThatThrownBy(() -> service.search(new AccountSearchRequest()))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo("KEYCLOAK_ADMIN_NOT_CONFIGURED");
                });

        when(keycloak.getUser("u-lan")).thenThrow(new KeycloakAdminException(
                KeycloakAdminException.STATUS_UNREACHABLE, "down"));
        assertThatThrownBy(() -> service.get("u-lan"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static KeycloakUser user(String id, String username, String email,
                                     String firstName, String lastName, boolean enabled) {
        KeycloakUser user = new KeycloakUser();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(enabled);
        user.setEmailVerified(true);
        user.setCreatedTimestamp(1_760_000_000_000L);
        return user;
    }
}
