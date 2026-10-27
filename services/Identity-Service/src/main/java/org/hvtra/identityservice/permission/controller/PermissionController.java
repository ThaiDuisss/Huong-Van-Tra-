package org.hvtra.identityservice.permission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.hvtra.commonservice.common_core.domain.response.ApiResponse;
import org.hvtra.commonservice.common_core.logging.action.UserAction;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.identityservice.account.model.StaffRole;
import org.hvtra.identityservice.permission.dto.PermissionDtos.BootstrapResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.BusinessRoleResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.CreatePermissionRequest;
import org.hvtra.identityservice.permission.dto.PermissionDtos.MyPermissionsResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.PermissionResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.RolePermissionsResponse;
import org.hvtra.identityservice.permission.dto.PermissionDtos.UpdatePermissionRequest;
import org.hvtra.identityservice.permission.dto.PermissionDtos.UpdateRolePermissionsRequest;
import org.hvtra.identityservice.permission.dto.PermissionDtos.UpdateUserRolesRequest;
import org.hvtra.identityservice.permission.dto.PermissionDtos.UserRolesResponse;
import org.hvtra.identityservice.permission.model.PermissionCatalog;
import org.hvtra.identityservice.permission.service.PermissionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API phân quyền. Backend là nơi quyết định: mọi endpoint ghi đều kiểm tra permission của người gọi
 * ({@code permission:manage}, {@code role:manage}, {@code user:manage}); SUPER_ADMIN luôn được phép (tài khoản gốc,
 * dùng để khởi tạo). Khi gateway chưa relay permission (triển khai cũ) thì tạm chấp nhận realm role ADMIN.
 */
@RestController
@RequestMapping("/api/identity")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    // ===== permissions =====

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> listPermissions(@RequestParam(value = "keyword", required = false) String keyword) {
        requireAny(PermissionCatalog.PERMISSION_MANAGE, PermissionCatalog.ROLE_MANAGE);
        return ApiResponse.success(permissionService.listPermissions(keyword));
    }

    @GetMapping("/permissions/{name}")
    public ApiResponse<PermissionResponse> getPermission(@PathVariable("name") String name) {
        requireAny(PermissionCatalog.PERMISSION_MANAGE, PermissionCatalog.ROLE_MANAGE);
        return ApiResponse.success(permissionService.getPermission(name));
    }

    @PostMapping("/permissions")
    @UserAction(value = "PERMISSION_CREATE", entityType = "Permission", entityId = "#result.data.name")
    public ApiResponse<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        require(PermissionCatalog.PERMISSION_MANAGE);
        return ApiResponse.created("Tạo quyền thành công", permissionService.createPermission(request));
    }

    @PutMapping("/permissions/{name}")
    @UserAction(value = "PERMISSION_UPDATE", entityType = "Permission", entityId = "#name")
    public ApiResponse<PermissionResponse> updatePermission(@PathVariable("name") String name,
                                                            @Valid @RequestBody UpdatePermissionRequest request) {
        require(PermissionCatalog.PERMISSION_MANAGE);
        return ApiResponse.updated("Cập nhật quyền thành công", permissionService.updatePermission(name, request));
    }

    @DeleteMapping("/permissions/{name}")
    @UserAction(value = "PERMISSION_DELETE", entityType = "Permission", entityId = "#name")
    public ApiResponse<Void> deletePermission(@PathVariable("name") String name) {
        require(PermissionCatalog.PERMISSION_MANAGE);
        permissionService.deletePermission(name);
        return ApiResponse.deleted("Đã xóa quyền");
    }

    /** Tạo danh mục quyền mặc định và gán mặc định cho vai trò (chỉ thêm, không gỡ). Dành cho ADMIN/SUPER_ADMIN. */
    @PostMapping("/permissions/bootstrap")
    @UserAction(value = "PERMISSION_BOOTSTRAP", entityType = "Permission", entityId = "'catalog'")
    public ApiResponse<BootstrapResponse> bootstrap() {
        requireAdminRole();
        return ApiResponse.success("Đã khởi tạo danh mục quyền", permissionService.bootstrap());
    }

    // ===== roles =====

    @GetMapping("/roles")
    public ApiResponse<List<BusinessRoleResponse>> listRoles(@AuthenticationPrincipal InternalUserPrincipal caller) {
        requireAny(PermissionCatalog.ROLE_MANAGE, PermissionCatalog.USER_MANAGE, PermissionCatalog.PERMISSION_MANAGE);
        return ApiResponse.success(permissionService.listBusinessRoles(caller));
    }

    @GetMapping("/roles/{role}/permissions")
    public ApiResponse<RolePermissionsResponse> getRolePermissions(@PathVariable("role") String role) {
        requireAny(PermissionCatalog.ROLE_MANAGE, PermissionCatalog.PERMISSION_MANAGE);
        return ApiResponse.success(permissionService.getRolePermissions(role));
    }

    @PutMapping("/roles/{role}/permissions")
    @UserAction(value = "ROLE_PERMISSIONS_UPDATE", entityType = "Role", entityId = "#role",
            detail = "'permissions=' + #request.permissions")
    public ApiResponse<RolePermissionsResponse> updateRolePermissions(
            @PathVariable("role") String role,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            @AuthenticationPrincipal InternalUserPrincipal caller) {
        require(PermissionCatalog.ROLE_MANAGE);
        return ApiResponse.updated("Cập nhật quyền của vai trò thành công",
                permissionService.updateRolePermissions(role, request.permissions(), caller));
    }

    // ===== user roles =====

    @GetMapping("/users/{userId}/roles")
    public ApiResponse<UserRolesResponse> getUserRoles(@PathVariable("userId") String userId) {
        require(PermissionCatalog.USER_MANAGE);
        return ApiResponse.success(permissionService.getUserRoles(userId));
    }

    @PutMapping("/users/{userId}/roles")
    @UserAction(value = "USER_ROLES_UPDATE", entityType = "Account", entityId = "#userId",
            detail = "'roles=' + #request.roles")
    public ApiResponse<UserRolesResponse> updateUserRoles(
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateUserRolesRequest request,
            @AuthenticationPrincipal InternalUserPrincipal caller) {
        require(PermissionCatalog.USER_MANAGE);
        return ApiResponse.updated("Cập nhật vai trò thành công",
                permissionService.updateUserRoles(userId, request.roles(), caller));
    }

    // ===== current user =====

    @GetMapping("/me/permissions")
    public ApiResponse<MyPermissionsResponse> myPermissions(@AuthenticationPrincipal InternalUserPrincipal caller) {
        if (caller == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        return ApiResponse.success(permissionService.myPermissions(caller));
    }

    // ===== authorization helpers =====

    private static final String[] LEGACY_ADMIN_FALLBACK = {"ADMIN"};

    private void require(String permission) {
        if (isSuperAdmin()) {
            return;
        }
        SecurityUtils.requirePermission(permission, LEGACY_ADMIN_FALLBACK);
    }

    private void requireAny(String... permissions) {
        if (isSuperAdmin()) {
            return;
        }
        for (String permission : permissions) {
            if (SecurityUtils.hasPermission(permission)) {
                return;
            }
        }
        // Không có permission nào: dùng quy tắc fallback của permission đầu tiên (chỉ hiệu lực với gateway cũ).
        SecurityUtils.requirePermission(permissions[0], LEGACY_ADMIN_FALLBACK);
    }

    /** Bootstrap dựa trên realm role vì lúc này chưa có permission nào tồn tại. */
    private void requireAdminRole() {
        if (isSuperAdmin() || SecurityUtils.hasRole("ADMIN")) {
            return;
        }
        throw new AccessDeniedException("Chỉ ADMIN hoặc SUPER_ADMIN được khởi tạo danh mục quyền");
    }

    private boolean isSuperAdmin() {
        return SecurityUtils.currentRoles().stream().anyMatch(StaffRole::isSuperAdmin);
    }
}
