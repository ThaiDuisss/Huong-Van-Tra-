package org.hvtra.identityservice.account.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Các vai trò nhân viên được phép gán qua màn quản lý tài khoản.
 * SUPER_ADMIN và CUSTOMER cố ý không có ở đây: không bao giờ gán/gỡ qua API này.
 */
public enum StaffRole {
    ADMIN,
    MANAGER,
    INVENTORY_STAFF,
    SALE_STAFF,
    QC,
    ACCOUNTANT,
    DELIVERY;

    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Tên role cũ/khác cách viết trong Keycloak, khớp với normalizeRole của pos-web. */
    private static final Map<String, String> ALIASES = Map.of(
            "WAREHOUSE_STAFF", "INVENTORY_STAFF",
            "WAREHOUSE", "INVENTORY_STAFF",
            "QC_STAFF", "QC",
            "SHIPPER", "DELIVERY",
            "SUPERADMIN", SUPER_ADMIN
    );

    public static List<String> names() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }

    /**
     * Chuẩn hóa tên role (từ Keycloak hoặc từ request): bỏ tiền tố ROLE_, viết hoa, quy đổi alias.
     * Ví dụ: "superadmin" -> "SUPER_ADMIN", "ROLE_qc_staff" -> "QC".
     */
    public static String canonicalName(String rawRole) {
        if (rawRole == null) {
            return "";
        }
        String upper = rawRole.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("ROLE_")) {
            upper = upper.substring("ROLE_".length());
        }
        return ALIASES.getOrDefault(upper, upper);
    }

    public static Optional<StaffRole> from(String rawRole) {
        String canonical = canonicalName(rawRole);
        return Arrays.stream(values())
                .filter(role -> role.name().equals(canonical))
                .findFirst();
    }

    public static boolean isSuperAdmin(String rawRole) {
        return SUPER_ADMIN.equals(canonicalName(rawRole));
    }
}
