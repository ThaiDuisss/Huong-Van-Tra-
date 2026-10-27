package org.hvtra.identityservice.permission.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.exception.BusinessException;
import org.hvtra.commonservice.common_core.exception.ResourceNotFoundException;
import org.hvtra.identityservice.account.client.KeycloakAdminClient;
import org.hvtra.identityservice.account.client.KeycloakAdminException;
import org.hvtra.identityservice.account.client.KeycloakClient;
import org.hvtra.identityservice.account.client.KeycloakRole;
import org.hvtra.identityservice.account.client.KeycloakUser;
import org.hvtra.identityservice.account.config.KeycloakAdminProperties;
import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.identityservice.permission.dto.PermissionDtos.BootstrapResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.BusinessRoleResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.CreatePermissionRequest;
import org.hvtra.identityservice.permission.dto.PermissionDtos.MyPermissionsResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.PermissionResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.RolePermissionsResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.UpdatePermissionRequest;
import org.hvtra.identityservice.permission.dto.PermissionDtos.UserRolesResponse;
import org.hvtra.identityservice.permission.model.PermissionCatalog;
import org.hvtra.identityservice.permission.model.PermissionCatalog.DefaultPermission;
import org.hvtra.identityservice.permission.model.PermissionName;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Quản lý quyền (permission = client role của client API trên Keycloak) và gán quyền cho vai trò nghiệp vụ
 * (composite realm role). Keycloak là nguồn dữ liệu duy nhất: metadata của quyền nằm trong
 * {@code description} và attribute {@code displayName} của role, không có bảng riêng.
 *
 * <p>Quy tắc an toàn:
 * <ul>
 *   <li>Chỉ các realm role trong {@link Role} được chỉnh composite; SUPER_ADMIN không bao giờ bị chạm.</li>
 *   <li>Người gọi không được sửa quyền của vai trò mình đang giữ (trừ SUPER_ADMIN) và không được sửa vai trò
 *       của chính mình: chặn tự nâng quyền.</li>
 *   <li>Không xóa quyền đang nằm trong vai trò hoặc gán trực tiếp cho user.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final Map<Role, String> ROLE_LABELS = Map.of(
            Role.SUPER_ADMIN, "Quản trị hệ thống",
            Role.MANAGER, "Quản lý",
            Role.INVENTORY_STAFF, "Nhân viên kho",
            Role.SALE_STAFF, "Nhân viên bán hàng",
            Role.QC, "Kiểm soát chất lượng"
    );

    private final KeycloakAdminClient keycloak;
    private final KeycloakAdminProperties properties;

    private volatile String cachedClientUuid;

    // ===== permissions =====

    public List<PermissionResponse> listPermissions(String keyword) {
        String needle = normalize(keyword);
        return withKeycloak(() -> {
            String clientUuid = permissionClientUuid();
            Map<String, List<String>> assignments = roleAssignments(clientUuid);
            return keycloak.listClientRoles(clientUuid).stream()
                    .filter(role -> PermissionName.isValid(role.name()))
                    .filter(role -> needle.isEmpty() || matches(role, needle))
                    .sorted((first, second) -> first.name().compareTo(second.name()))
                    .map(role -> toResponse(role, assignments.getOrDefault(role.name(), List.of())))
                    .toList();
        });
    }

    public PermissionResponse getPermission(String rawName) {
        String name = PermissionName.normalize(rawName);
        return withKeycloak(() -> {
            String clientUuid = permissionClientUuid();
            KeycloakRole role = keycloak.getClientRole(clientUuid, name);
            return toResponse(role, roleAssignments(clientUuid).getOrDefault(name, List.of()));
        });
    }

    public PermissionResponse createPermission(CreatePermissionRequest request) {
        String name = PermissionName.normalize(request.name());
        return withKeycloak(() -> {
            String clientUuid = permissionClientUuid();
            if (findClientRole(clientUuid, name) != null) {
                throw new BusinessException("PERMISSION_ALREADY_EXISTS", "Quyền " + name + " đã tồn tại",
                        HttpStatus.CONFLICT);
            }
            keycloak.createClientRole(clientUuid, roleRepresentation(null, name, request.displayName(), request.description()));
            return toResponse(keycloak.getClientRole(clientUuid, name), List.of());
        });
    }

    public PermissionResponse updatePermission(String rawName, UpdatePermissionRequest request) {
        String name = PermissionName.normalize(rawName);
        return withKeycloak(() -> {
            String clientUuid = permissionClientUuid();
            KeycloakRole current = keycloak.getClientRole(clientUuid, name);
            keycloak.updateClientRole(clientUuid, name,
                    roleRepresentation(current.id(), name, request.displayName(), request.description()));
            return toResponse(keycloak.getClientRole(clientUuid, name),
                    roleAssignments(clientUuid).getOrDefault(name, List.of()));
        });
    }

    /** Chỉ xóa khi không còn vai trò nào chứa quyền và không user nào được gán trực tiếp. */
    public void deletePermission(String rawName) {
        String name = PermissionName.normalize(rawName);
        withKeycloak(() -> {
            String clientUuid = permissionClientUuid();
            KeycloakRole role = keycloak.getClientRole(clientUuid, name);
            List<String> assigned = roleAssignments(clientUuid).getOrDefault(name, List.of());
            if (!assigned.isEmpty()) {
                throw new BusinessException("PERMISSION_IN_USE",
                        "Quyền đang được gán cho vai trò: " + String.join(", ", assigned)
                                + ". Gỡ khỏi các vai trò trước khi xóa.",
                        HttpStatus.CONFLICT);
            }
            List<KeycloakUser> directUsers = keycloak.listUsersInClientRole(clientUuid, role.name());
            if (!directUsers.isEmpty()) {
                throw new BusinessException("PERMISSION_IN_USE",
                        "Quyền đang được gán trực tiếp cho " + directUsers.size() + " tài khoản trên Keycloak",
                        HttpStatus.CONFLICT);
            }
            keycloak.deleteClientRole(clientUuid, name);
            return null;
        });
    }

    // ===== business roles =====

    public List<BusinessRoleResponse> listBusinessRoles(InternalUserPrincipal caller) {
        return withKeycloak(() -> {
            String clientUuid = permissionClientUuid();
            Map<String, KeycloakRole> realmRoles = realmRoleIndex();
            List<BusinessRoleResponse> result = new ArrayList<>();
            for (Role role : Role.assignable()) {
                KeycloakRole realmRole = realmRoles.get(role.name());
                if (realmRole == null) {
                    continue;
                }
                int count = keycloak.getRealmRoleClientComposites(realmRole.name(), clientUuid).size();
                result.add(new BusinessRoleResponse(role.name(), ROLE_LABELS.getOrDefault(role, role.name()),
                        canEditRole(caller, role), count));
            }
            return result;
        });
    }

    public RolePermissionsResponse getRolePermissions(String rawRole) {
        Role role = parseBusinessRole(rawRole);
        return withKeycloak(() -> {
            KeycloakRole realmRole = requireRealmRole(role);
            return new RolePermissionsResponse(role.name(),
                    permissionNames(keycloak.getRealmRoleClientComposites(realmRole.name(), permissionClientUuid())));
        });
    }

    /**
     * Đồng bộ composite của vai trò về đúng tập quyền yêu cầu: chỉ thêm/gỡ client role của client permission,
     * không đụng tới composite khác (realm role lồng nhau, client khác). Thêm trước, gỡ sau.
     */
    public RolePermissionsResponse updateRolePermissions(String rawRole, List<String> rawPermissions,
                                                         InternalUserPrincipal caller) {
        Role role = parseBusinessRole(rawRole);
        if (!canEditRole(caller, role)) {
            throw new BusinessException("ROLE_SELF_EDIT",
                    "Bạn không thể thay đổi quyền của vai trò mình đang giữ (" + role.name() + ")",
                    HttpStatus.FORBIDDEN);
        }
        Set<String> desired = new TreeSet<>();
        for (String raw : rawPermissions) {
            desired.add(PermissionName.normalize(raw));
        }
        return withKeycloak(() -> {
            String clientUuid = permissionClientUuid();
            KeycloakRole realmRole = requireRealmRole(role);
            Map<String, KeycloakRole> available = new LinkedHashMap<>();
            keycloak.listClientRoles(clientUuid).forEach(permission -> available.put(permission.name(), permission));
            List<String> unknown = desired.stream().filter(name -> !available.containsKey(name)).toList();
            if (!unknown.isEmpty()) {
                throw new BusinessException("PERMISSION_NOT_FOUND",
                        "Quyền không tồn tại: " + String.join(", ", unknown));
            }
            List<KeycloakRole> current = keycloak.getRealmRoleClientComposites(realmRole.name(), clientUuid);
            Set<String> currentNames = new TreeSet<>(permissionNames(current));
            List<KeycloakRole> toAdd = desired.stream()
                    .filter(name -> !currentNames.contains(name))
                    .map(available::get)
                    .map(PermissionService::idAndName)
                    .toList();
            List<KeycloakRole> toRemove = current.stream()
                    .filter(permission -> !desired.contains(permission.name()))
                    .map(PermissionService::idAndName)
                    .toList();
            keycloak.addRealmRoleComposites(realmRole.name(), toAdd);
            try {
                keycloak.removeRealmRoleComposites(realmRole.name(), toRemove);
            } catch (KeycloakAdminException ex) {
                // Đã thêm xong nhưng gỡ lỗi: báo rõ trạng thái thực tế thay vì coi như thành công.
                log.error("Composite update of role {} partially applied: added={} removeFailed={}",
                        role, permissionNames(toAdd), permissionNames(toRemove));
                throw new BusinessException("ROLE_PERMISSIONS_PARTIAL",
                        "Đã thêm quyền mới nhưng chưa gỡ được quyền cũ (" + ex.getMessage()
                                + "). Tải lại và thử lại.", HttpStatus.BAD_GATEWAY);
            }
            return new RolePermissionsResponse(role.name(),
                    permissionNames(keycloak.getRealmRoleClientComposites(realmRole.name(), clientUuid)));
        });
    }

    // ===== user roles =====

    public UserRolesResponse getUserRoles(String userId) {
        return withKeycloak(() -> {
            KeycloakUser user = keycloak.getUser(userId);
            Set<String> roles = canonicalStaffRoles(keycloak.getUserRealmRoles(userId));
            return toUserRoles(user, roles, effectivePermissions(userId));
        });
    }

    /** Chỉ thêm/gỡ các vai trò trong {@link Role}; role khác (default-roles, CUSTOMER, SUPER_ADMIN) giữ nguyên. */
    public UserRolesResponse updateUserRoles(String userId, List<String> rawRoles, InternalUserPrincipal caller) {
        if (caller != null && userId != null && userId.equals(caller.userId())) {
            throw new BusinessException("USER_SELF_ROLE_CHANGE",
                    "Bạn không thể thay đổi vai trò của chính mình", HttpStatus.FORBIDDEN);
        }
        Set<Role> desired = EnumSet.noneOf(Role.class);
        for (String raw : rawRoles) {
            desired.add(Role.fromName(raw).filter(Role::isAssignable).orElseThrow(() -> new BusinessException("ROLE_NOT_ALLOWED",
                    "Vai trò không hợp lệ hoặc không được phép gán: " + raw)));
        }
        if (desired.isEmpty()) {
            throw new BusinessException("ROLE_REQUIRED", "Nhân viên phải có ít nhất một vai trò");
        }
        return withKeycloak(() -> {
            KeycloakUser user = keycloak.getUser(userId);
            List<KeycloakRole> currentRealmRoles = keycloak.getUserRealmRoles(userId);
            Set<String> currentNames = canonicalStaffRoles(currentRealmRoles);
            if (currentRealmRoles.stream().anyMatch(role -> Role.SUPER_ADMIN.matches(role.name()))) {
                throw new BusinessException("ACCOUNT_PROTECTED",
                        "Không thể thay đổi vai trò của tài khoản SUPER_ADMIN", HttpStatus.FORBIDDEN);
            }
            Map<String, KeycloakRole> index = realmRoleIndex();
            List<KeycloakRole> toAdd = new ArrayList<>();
            for (Role role : desired) {
                if (!currentNames.contains(role.name())) {
                    KeycloakRole realmRole = index.get(role.name());
                    if (realmRole == null) {
                        throw new BusinessException("ROLE_NOT_FOUND",
                                "Vai trò " + role.name() + " chưa được tạo trong Keycloak");
                    }
                    toAdd.add(idAndName(realmRole));
                }
            }
            Set<String> desiredNames = new TreeSet<>();
            desired.forEach(role -> desiredNames.add(role.name()));
            List<KeycloakRole> toRemove = currentRealmRoles.stream()
                    .filter(role -> Role.fromName(role.name()).filter(Role::isAssignable).isPresent())
                    .filter(role -> !desiredNames.contains(Role.canonicalName(role.name())))
                    .map(PermissionService::idAndName)
                    .toList();
            keycloak.addUserRealmRoles(userId, toAdd);
            keycloak.removeUserRealmRoles(userId, toRemove);
            return toUserRoles(user, desiredNames, effectivePermissions(userId));
        });
    }

    // ===== current user =====

    /** Quyền hiệu lực của người gọi: lấy tươi từ Keycloak; Keycloak lỗi thì tạm dùng claim trong token. */
    public MyPermissionsResponse myPermissions(InternalUserPrincipal caller) {
        List<String> roles = caller.roles() == null ? List.of() : new ArrayList<>(new TreeSet<>(caller.roles()));
        try {
            return new MyPermissionsResponse(caller.userId(), roles, effectivePermissions(caller.userId()), "keycloak");
        } catch (KeycloakAdminException ex) {
            log.warn("Cannot load effective permissions from Keycloak (status={}), using token claims", ex.getStatus());
            List<String> fromToken = caller.permissions() == null
                    ? List.of()
                    : new ArrayList<>(new TreeSet<>(caller.permissions()));
            return new MyPermissionsResponse(caller.userId(), roles, fromToken, "token");
        }
    }

    // ===== bootstrap =====

    /** Tạo quyền mặc định còn thiếu và thêm vào composite vai trò còn thiếu. Không gỡ gì. */
    public BootstrapResponse bootstrap() {
        return withKeycloak(() -> {
            String clientUuid = permissionClientUuid();
            Map<String, KeycloakRole> existing = new LinkedHashMap<>();
            keycloak.listClientRoles(clientUuid).forEach(role -> existing.put(role.name(), role));
            List<String> created = new ArrayList<>();
            for (DefaultPermission permission : PermissionCatalog.DEFAULT_PERMISSIONS) {
                if (!existing.containsKey(permission.name())) {
                    keycloak.createClientRole(clientUuid,
                            roleRepresentation(null, permission.name(), permission.displayName(), permission.description()));
                    created.add(permission.name());
                }
            }
            if (!created.isEmpty()) {
                existing.clear();
                keycloak.listClientRoles(clientUuid).forEach(role -> existing.put(role.name(), role));
            }
            Map<String, KeycloakRole> realmRoles = realmRoleIndex();
            List<String> updatedRoles = new ArrayList<>();
            for (Map.Entry<Role, Set<String>> entry : PermissionCatalog.DEFAULT_ROLE_PERMISSIONS.entrySet()) {
                KeycloakRole realmRole = realmRoles.get(entry.getKey().name());
                if (realmRole == null) {
                    log.warn("Bootstrap skips role {}: realm role missing in Keycloak", entry.getKey());
                    continue;
                }
                Set<String> current = new TreeSet<>(permissionNames(
                        keycloak.getRealmRoleClientComposites(realmRole.name(), clientUuid)));
                List<KeycloakRole> toAdd = entry.getValue().stream()
                        .filter(name -> !current.contains(name) && existing.containsKey(name))
                        .sorted()
                        .map(existing::get)
                        .map(PermissionService::idAndName)
                        .toList();
                if (!toAdd.isEmpty()) {
                    keycloak.addRealmRoleComposites(realmRole.name(), toAdd);
                    updatedRoles.add(entry.getKey().name());
                }
            }
            return new BootstrapResponse(created, updatedRoles, existing.size());
        });
    }

    // ===== helpers =====

    private boolean canEditRole(InternalUserPrincipal caller, Role role) {
        if (caller == null || caller.roles() == null) {
            return true;
        }
        boolean superAdmin = caller.roles().stream().anyMatch(Role.SUPER_ADMIN::matches);
        if (superAdmin) {
            return true;
        }
        return caller.roles().stream()
                .map(Role::canonicalName)
                .noneMatch(role.name()::equals);
    }

    private Role parseBusinessRole(String rawRole) {
        return Role.fromName(rawRole).filter(Role::isAssignable).orElseThrow(() -> new BusinessException("ROLE_NOT_ALLOWED",
                "Vai trò không hợp lệ hoặc không quản lý được qua API này: " + rawRole));
    }

    private List<String> effectivePermissions(String userId) {
        return permissionNames(keycloak.getUserEffectiveClientRoles(userId, permissionClientUuid()));
    }

    /** permission name -> các vai trò nghiệp vụ (canonical) chứa nó. */
    private Map<String, List<String>> roleAssignments(String clientUuid) {
        Map<String, List<String>> result = new TreeMap<>();
        for (Map.Entry<String, KeycloakRole> entry : realmRoleIndex().entrySet()) {
            for (KeycloakRole permission : keycloak.getRealmRoleClientComposites(entry.getValue().name(), clientUuid)) {
                result.computeIfAbsent(permission.name(), key -> new ArrayList<>()).add(entry.getKey());
            }
        }
        return result;
    }

    /** canonical Role name -> realm role thật trên Keycloak (ưu tiên trùng tên, sau đó alias). */
    private Map<String, KeycloakRole> realmRoleIndex() {
        Map<String, KeycloakRole> index = new LinkedHashMap<>();
        for (KeycloakRole role : keycloak.listRealmRoles()) {
            String canonical = Role.canonicalName(role.name());
            if (Role.fromName(canonical).filter(Role::isAssignable).isEmpty()) {
                continue;
            }
            KeycloakRole existing = index.get(canonical);
            if (existing == null || role.name().equalsIgnoreCase(canonical)) {
                index.put(canonical, role);
            }
        }
        return index;
    }

    private KeycloakRole requireRealmRole(Role role) {
        KeycloakRole realmRole = realmRoleIndex().get(role.name());
        if (realmRole == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "Vai trò " + role.name() + " chưa được tạo trong Keycloak");
        }
        return realmRole;
    }

    private Set<String> canonicalStaffRoles(Collection<KeycloakRole> realmRoles) {
        Set<String> roles = new TreeSet<>();
        for (KeycloakRole role : realmRoles) {
            String canonical = Role.canonicalName(role.name());
            if (Role.fromName(canonical).isPresent()) {
                roles.add(canonical);
            }
        }
        return roles;
    }

    private KeycloakRole findClientRole(String clientUuid, String name) {
        try {
            return keycloak.getClientRole(clientUuid, name);
        } catch (KeycloakAdminException ex) {
            if (ex.getStatus() == 404) {
                return null;
            }
            throw ex;
        }
    }

    String permissionClientUuid() {
        String cached = cachedClientUuid;
        if (cached != null) {
            return cached;
        }
        KeycloakClient client = keycloak.findClientByClientId(properties.getPermissionClientId());
        if (client == null || !StringUtils.hasText(client.id())) {
            throw new BusinessException("PERMISSION_CLIENT_NOT_FOUND",
                    "Client " + properties.getPermissionClientId() + " chưa tồn tại trên Keycloak",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        cachedClientUuid = client.id();
        return client.id();
    }

    private static KeycloakRole roleRepresentation(String id, String name, String displayName, String description) {
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        attributes.put(PermissionCatalog.ATTRIBUTE_DISPLAY_NAME, List.of(displayName == null ? name : displayName.trim()));
        return new KeycloakRole(id, name, description == null ? "" : description.trim(), null, true, attributes);
    }

    private static PermissionResponse toResponse(KeycloakRole role, List<String> assignedRoles) {
        String displayName = role.attribute(PermissionCatalog.ATTRIBUTE_DISPLAY_NAME);
        boolean builtIn = PermissionCatalog.DEFAULT_PERMISSIONS.stream()
                .anyMatch(permission -> permission.name().equals(role.name()));
        return new PermissionResponse(role.id(), role.name(),
                StringUtils.hasText(displayName) ? displayName : role.name(),
                role.description() == null ? "" : role.description(),
                assignedRoles, builtIn);
    }

    private UserRolesResponse toUserRoles(KeycloakUser user, Set<String> roles, List<String> permissions) {
        String fullName = ((user.getLastName() == null ? "" : user.getLastName().trim()) + " "
                + (user.getFirstName() == null ? "" : user.getFirstName().trim())).trim();
        return new UserRolesResponse(user.getId(), user.getUsername(), fullName,
                !roles.contains(Role.SUPER_ADMIN.name()), List.copyOf(roles), permissions);
    }

    private static List<String> permissionNames(Collection<KeycloakRole> roles) {
        Set<String> names = new TreeSet<>();
        roles.forEach(role -> names.add(role.name()));
        return new ArrayList<>(names);
    }

    private static KeycloakRole idAndName(KeycloakRole role) {
        return new KeycloakRole(role.id(), role.name());
    }

    private static boolean matches(KeycloakRole role, String needle) {
        return normalize(role.name()).contains(needle)
                || normalize(role.description()).contains(needle)
                || normalize(role.attribute(PermissionCatalog.ATTRIBUTE_DISPLAY_NAME)).contains(needle);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private <T> T withKeycloak(Supplier<T> action) {
        try {
            return action.get();
        } catch (KeycloakAdminException ex) {
            throw translate(ex);
        }
    }

    private RuntimeException translate(KeycloakAdminException ex) {
        String detail = ex.getMessage() == null ? "" : ex.getMessage();
        return switch (ex.getStatus()) {
            case 404 -> new ResourceNotFoundException("Không tìm thấy quyền, vai trò hoặc tài khoản trên Keycloak");
            case 409 -> new BusinessException("PERMISSION_ALREADY_EXISTS", "Quyền đã tồn tại", HttpStatus.CONFLICT);
            case 400 -> new BusinessException("PERMISSION_INVALID", "Keycloak từ chối dữ liệu: " + detail);
            case 401, 403 -> {
                log.error("Keycloak admin API rejected the service account for permission management: status={}",
                        ex.getStatus());
                yield new BusinessException("KEYCLOAK_ADMIN_FORBIDDEN",
                        "Service account của Identity-Service chưa đủ quyền (cần view-clients, manage-clients, "
                                + "view-realm, manage-realm, manage-users)", HttpStatus.BAD_GATEWAY);
            }
            case KeycloakAdminException.STATUS_NOT_CONFIGURED -> new BusinessException(
                    "KEYCLOAK_ADMIN_NOT_CONFIGURED",
                    "Chức năng phân quyền chưa được cấu hình kết nối Keycloak", HttpStatus.SERVICE_UNAVAILABLE);
            case KeycloakAdminException.STATUS_UNREACHABLE -> new BusinessException(
                    "KEYCLOAK_UNAVAILABLE", "Không kết nối được Keycloak, vui lòng thử lại sau",
                    HttpStatus.SERVICE_UNAVAILABLE);
            default -> {
                log.error("Keycloak admin API error: status={} message={}", ex.getStatus(), detail);
                yield new BusinessException("KEYCLOAK_ERROR", "Keycloak gặp lỗi khi xử lý yêu cầu",
                        HttpStatus.BAD_GATEWAY);
            }
        };
    }
}
