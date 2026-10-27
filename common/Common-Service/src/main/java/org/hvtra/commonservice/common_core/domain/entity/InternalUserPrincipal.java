package org.hvtra.commonservice.common_core.domain.entity;

import java.util.Set;

/**
 * Người dùng nội bộ do gateway xác thực và relay sang service.
 *
 * @param roles       realm role nghiệp vụ (ADMIN, SALE_STAFF, ...), không có tiền tố ROLE_
 * @param permissions permission hiệu lực (client role, vd. {@code order:create}); {@code null} khi gateway cũ
 *                    chưa relay header permission, {@code Set.of()} khi gateway relay nhưng user không có quyền nào
 */
public record InternalUserPrincipal(
        String userId,
        String username,
        String issuer,
        Set<String> roles,
        String correlationId,
        Set<String> permissions
) {
    /** Giữ constructor 5 tham số cho code và test cũ: coi như gateway chưa relay permission. */
    public InternalUserPrincipal(String userId, String username, String issuer, Set<String> roles, String correlationId) {
        this(userId, username, issuer, roles, correlationId, null);
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /** true khi gateway đã relay danh sách permission (kể cả rỗng). */
    public boolean permissionsKnown() {
        return permissions != null;
    }
}