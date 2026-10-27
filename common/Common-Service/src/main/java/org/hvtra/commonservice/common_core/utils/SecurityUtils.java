package org.hvtra.commonservice.common_core.utils;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Authentication authentication() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Unauthenticated");
        }

        return authentication;
    }

    public static InternalUserPrincipal currentUser() {
        Object principal = authentication().getPrincipal();

        if (!(principal instanceof InternalUserPrincipal internalUser)) {
            throw new AccessDeniedException("Invalid internal user principal");
        }

        return internalUser;
    }

    public static String currentUserId() {
        return currentUser().userId();
    }

    public static String currentUsername() {
        return currentUser().username();
    }


    public static Set<String> currentRoles() {
        return currentUser().roles();
    }



    public static String currentCorrelationId() {
        return currentUser().correlationId();
    }

    public static boolean hasRole(Role role) {
        String authorityName = role.authority();
        return authentication().getAuthorities()
                .stream()
                .anyMatch(authority -> authority.getAuthority().equals(authorityName));
    }

    public static boolean hasAnyRole(Role... roles) {
        for (Role role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    /** Permission hiệu lực (client role Keycloak, vd. order:create) có trong authorities. */
    public static boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        return authentication().getAuthorities()
                .stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }

    /**
     * Bắt buộc có permission. Nếu gateway chưa relay permission (triển khai cũ hoặc Keycloak chưa cấu hình
     * client role) thì chấp nhận một trong {@code fallbackRoles} để không khóa chức năng đang chạy; khi
     * gateway đã relay (kể cả danh sách rỗng) thì chỉ permission mới được tính.
     */
    public static void requirePermission(String permission, Role... fallbackRoles) {
        if (hasPermission(permission)) {
            return;
        }
        Object principal = authentication().getPrincipal();
        boolean permissionsKnown = principal instanceof InternalUserPrincipal internalUser
                && internalUser.permissionsKnown();
        if (!permissionsKnown && fallbackRoles != null) {
            for (Role role : fallbackRoles) {
                if (hasRole(role)) {
                    return;
                }
            }
        }
        throw new AccessDeniedException("Thiếu quyền " + permission);
    }

    public static boolean isSuperAdmin() {
        return hasRole(Role.SUPER_ADMIN);
    }
}