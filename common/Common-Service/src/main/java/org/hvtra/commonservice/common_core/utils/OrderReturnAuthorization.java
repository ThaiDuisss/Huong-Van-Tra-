package org.hvtra.commonservice.common_core.utils;

import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.domain.entity.InternalUserPrincipal;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.Set;

/** Action boundaries shared by Order, Inventory and Payment. Permissions never replace the role boundary. */
public final class OrderReturnAuthorization {
    private static final Role[] SALES = {Role.SALE_STAFF, Role.MANAGER, Role.SUPER_ADMIN};
    private static final Role[] WAREHOUSE = {Role.INVENTORY_STAFF, Role.QC, Role.MANAGER, Role.SUPER_ADMIN};
    private static final Role[] QC = {Role.QC, Role.MANAGER, Role.SUPER_ADMIN};

    private OrderReturnAuthorization() {
    }

    public static void requireRead() {
        requireReadPermission("order:view");
    }

    public static void requireCreate() {
        requireAction("order:return", SALES);
    }

    public static void requireReceive() {
        Set<String> realmRoles = SecurityUtils.currentUser().roles();
        if (realmRoles == null || Arrays.stream(WAREHOUSE)
                .noneMatch(role -> realmRoles.stream().anyMatch(role::matches))) {
            throw new AccessDeniedException("Vai trò không được thực hiện thao tác trả hàng này");
        }
    }

    public static void requireQc() {
        requireAction("inventory:qc", QC);
    }

    public static void requireDisposition() {
        requireQc();
        requireAction("inventory:adjust", QC);
    }

    public static void requireRefund() {
        requireAction("order:refund", SALES);
    }

    public static void requireWarehouseRead() {
        if (isInternalServiceUser()) return;
        requireReadPermission("inventory:view");
    }

    /** Read-only evidence is also needed by authenticated background reconciliation jobs. */
    public static void requireEvidenceRead() {
        if (isInternalServiceUser()) return;
        requireRead();
    }

    private static boolean isInternalServiceUser() {
        InternalUserPrincipal user = SecurityUtils.currentUser();
        return user.userId() != null && user.userId().startsWith("service:")
                && user.roles() != null && user.roles().contains("SYSTEM");
    }

    private static void requireReadPermission(String salesPermission) {
        if (SecurityUtils.isSuperAdmin()) return;
        InternalUserPrincipal user = SecurityUtils.currentUser();
        boolean sales = SecurityUtils.hasAnyRole(SALES)
                && (!user.permissionsKnown() || SecurityUtils.hasPermission(salesPermission));
        boolean warehouse = SecurityUtils.hasAnyRole(WAREHOUSE)
                && (!user.permissionsKnown() || SecurityUtils.hasPermission("inventory:view"));
        if (!sales && !warehouse) {
            throw new AccessDeniedException("Bạn không có quyền xem phiếu hoàn hàng");
        }
    }

    private static void requireAction(String permission, Role... roles) {
        if (SecurityUtils.isSuperAdmin()) return;
        if (!SecurityUtils.hasAnyRole(roles)) {
            throw new AccessDeniedException("Vai trò không được thực hiện thao tác trả hàng này");
        }
        SecurityUtils.requirePermission(permission, roles);
    }
}
