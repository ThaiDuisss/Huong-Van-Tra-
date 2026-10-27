package org.hvtra.commonservice.common_core.domain.constants;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Vai trò nghiệp vụ dùng chung cho mọi service (realm role Keycloak). Mọi chỗ kiểm tra / khai báo role phải dùng enum
 * này, không viết chuỗi role thủ công.
 * <p>
 * Tên cũ được quy về role chuẩn khi xác thực ({@link #fromName(String)}): ADMIN / SUPERADMIN → SUPER_ADMIN,
 * BRANCH_MANAGER / INVENTORY_MANAGER / DIRECTOR → MANAGER, WAREHOUSE / WAREHOUSE_STAFF → INVENTORY_STAFF,
 * QC_STAFF → QC, SALES_STAFF / POS_STAFF / CASHIER / STAFF → SALE_STAFF.
 */
public enum Role {
    /** Tài khoản quản trị gốc: toàn quyền, không chỉnh sửa / gán qua API quản lý tài khoản. */
    SUPER_ADMIN,
    MANAGER,
    INVENTORY_STAFF,
    SALE_STAFF,
    QC;

    private static final String PREFIX = "ROLE_";

    private static final Map<String, Role> ALIASES = Map.ofEntries(
            Map.entry("ADMIN", SUPER_ADMIN),
            Map.entry("SUPERADMIN", SUPER_ADMIN),
            Map.entry("BRANCH_MANAGER", MANAGER),
            Map.entry("INVENTORY_MANAGER", MANAGER),
            Map.entry("DIRECTOR", MANAGER),
            Map.entry("WAREHOUSE", INVENTORY_STAFF),
            Map.entry("WAREHOUSE_STAFF", INVENTORY_STAFF),
            Map.entry("QC_STAFF", QC),
            Map.entry("SALES_STAFF", SALE_STAFF),
            Map.entry("POS_STAFF", SALE_STAFF),
            Map.entry("CASHIER", SALE_STAFF),
            Map.entry("STAFF", SALE_STAFF)
    );

    /** Authority của Spring Security, vd. {@code ROLE_MANAGER}. */
    public String authority() {
        return PREFIX + name();
    }

    /**
     * Quy tên role thô (có / không tiền tố ROLE_, hoa / thường, gạch ngang, alias cũ) về role chuẩn.
     * Role không thuộc hệ thống (vd. offline_access) trả về empty.
     */
    public static Optional<Role> fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String name = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (name.startsWith(PREFIX)) {
            name = name.substring(PREFIX.length());
        }
        String canonical = name;
        return Arrays.stream(values())
                .filter(role -> role.name().equals(canonical))
                .findFirst()
                .or(() -> Optional.ofNullable(ALIASES.get(canonical)));
    }

    /**
     * Tên chuẩn của role thô (vd. realm role trên Keycloak): role hệ thống thì trả tên enum, role khác
     * (default-roles-..., offline_access) trả tên viết hoa đã bỏ tiền tố ROLE_.
     */
    public static String canonicalName(String raw) {
        return fromName(raw).map(Role::name).orElseGet(() -> {
            String name = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            return name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : name;
        });
    }

    /** Role thô (có thể là alias / có tiền tố) có quy về đúng role này không. */
    public boolean matches(String raw) {
        return fromName(raw).filter(this::equals).isPresent();
    }

    /** Role được gán / gỡ qua màn quản lý tài khoản: mọi role trừ SUPER_ADMIN (chỉ cấp trên Keycloak). */
    public boolean isAssignable() {
        return this != SUPER_ADMIN;
    }

    /** Các role được gán qua màn quản lý tài khoản, theo thứ tự khai báo. */
    public static List<Role> assignable() {
        return Arrays.stream(values()).filter(Role::isAssignable).toList();
    }

    /** Tên role (không tiền tố) để truyền cho {@code hasAnyRole(String...)} của Spring Security. */
    public static String[] names(Role... roles) {
        return Arrays.stream(roles).map(Role::name).toArray(String[]::new);
    }
}
