package org.hvtra.identityservice.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** DTO của API phân quyền (gom một file cho gọn; mỗi record là một contract). */
public final class PermissionDtos {

    private PermissionDtos() {
    }

    /**
     * @param name          định danh ổn định, trùng tên client role Keycloak (vd. order:create)
     * @param displayName   tên hiển thị (attribute displayName của role)
     * @param description   mô tả (description của role)
     * @param assignedRoles vai trò nghiệp vụ đang chứa quyền này
     * @param builtIn       có trong danh mục mặc định của hệ thống
     */
    public record PermissionResponse(
            String id,
            String name,
            String displayName,
            String description,
            List<String> assignedRoles,
            boolean builtIn
    ) {
    }

    public record CreatePermissionRequest(
            @NotBlank(message = "Tên quyền không được để trống")
            @Size(max = 64, message = "Tên quyền tối đa 64 ký tự")
            String name,
            @NotBlank(message = "Tên hiển thị không được để trống")
            @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự")
            String displayName,
            @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
            String description
    ) {
    }

    /** Không cho đổi {@code name}: đổi tên client role sẽ làm lệch composite và kiểm tra quyền ở backend. */
    public record UpdatePermissionRequest(
            @NotBlank(message = "Tên hiển thị không được để trống")
            @Size(max = 100, message = "Tên hiển thị tối đa 100 ký tự")
            String displayName,
            @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
            String description
    ) {
    }

    /** @param editable false khi người gọi không được sửa quyền của vai trò này (vai trò mình đang giữ). */
    public record BusinessRoleResponse(String name, String label, boolean editable, int permissionCount) {
    }

    public record RolePermissionsResponse(String role, List<String> permissions) {
    }

    public record UpdateRolePermissionsRequest(
            @NotNull(message = "Danh sách quyền không được null")
            List<String> permissions
    ) {
    }

    /**
     * @param roles                vai trò nghiệp vụ gán trực tiếp
     * @param effectivePermissions quyền hiệu lực theo Keycloak (gồm kế thừa)
     */
    public record UserRolesResponse(
            String userId,
            String username,
            String fullName,
            boolean editable,
            List<String> roles,
            List<String> effectivePermissions
    ) {
    }

    public record UpdateUserRolesRequest(
            @NotNull(message = "Danh sách vai trò không được null")
            List<String> roles
    ) {
    }

    /** @param source "keycloak" khi lấy tươi từ Keycloak, "token" khi tạm dùng claim trong access token. */
    public record MyPermissionsResponse(String userId, List<String> roles, List<String> permissions, String source) {
    }

    public record BootstrapResponse(
            List<String> createdPermissions,
            List<String> updatedRoles,
            int totalPermissions
    ) {
    }
}
