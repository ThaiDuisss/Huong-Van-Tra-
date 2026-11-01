package org.hvtra.identityservice.account.dto;

import java.time.Instant;
import java.util.List;

/**
 * Tài khoản nhân viên (dữ liệu lấy trực tiếp từ Keycloak).
 *
 * @param id       Keycloak user id (JWT sub)
 * @param roles    vai trò đã chuẩn hóa, không có tiền tố ROLE_ (vd. MANAGER, SALE_STAFF, SUPER_ADMIN)
 * @param editable false với tài khoản SUPER_ADMIN: không được sửa/khóa qua API này
 */
public record AccountResponse(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        String fullName,
        boolean enabled,
        boolean emailVerified,
        List<String> roles,
        Instant createdAt,
        boolean editable
) {
}
