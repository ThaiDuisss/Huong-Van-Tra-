import { useAuth } from "./auth-context";
import { hasAllRoles, hasAnyRole, hasRole, normalizeRole } from "./auth.utils";
import { ROLES, type Role } from "./roles";

export function useAuthorization() {
    const {
        roles: rawRoles = [],
        authenticated,
        initialized,
        user,
        keycloakSubject,
        permissions,
        permissionsLoaded,
        hasPermission: hasPermissionFn,
        refreshPermissions
    } = useAuth();
    const roles = rawRoles.map(normalizeRole) as Role[];

    return {
        roles,
        authenticated,
        initialized,
        user,
        keycloakSubject,
        /** undefined = chưa tải được permission (backend cũ): dùng role như trước. */
        permissions,
        permissionsLoaded,
        hasPermission: hasPermissionFn,
        refreshPermissions,

        hasRole: (role: keyof typeof ROLES | Role) => {
            const targetRole = role in ROLES ? ROLES[role as keyof typeof ROLES] : (role as Role);
            return hasRole(roles, targetRole);
        },

        hasAnyRole: (requiredRoles: (keyof typeof ROLES | Role)[]) => {
            const mappedRoles = requiredRoles.map((r) =>
                r in ROLES ? ROLES[r as keyof typeof ROLES] : (r as Role)
            );
            return hasAnyRole(roles, mappedRoles);
        },

        hasAllRoles: (requiredRoles: (keyof typeof ROLES | Role)[]) => {
            const mappedRoles = requiredRoles.map((r) =>
                r in ROLES ? ROLES[r as keyof typeof ROLES] : (r as Role)
            );
            return hasAllRoles(roles, mappedRoles);
        },

        isAccountant: () => hasRole(roles, ROLES.ACCOUNTANT),
        isAdmin: () => hasRole(roles, ROLES.ADMIN),
        isManager: () => hasRole(roles, ROLES.MANAGER),
        isCustomer: () => hasRole(roles, ROLES.CUSTOMER),
        isDelivery: () => hasRole(roles, ROLES.DELIVERY),
        isInventoryStaff: () => hasRole(roles, ROLES.INVENTORY_STAFF),
        isSaleStaff: () => hasRole(roles, ROLES.SALE_STAFF),
        isQc: () => hasRole(roles, ROLES.QC)
    };
}
