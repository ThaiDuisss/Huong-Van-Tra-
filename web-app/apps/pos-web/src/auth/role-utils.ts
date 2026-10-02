import { hasAllRoles, hasAnyRole, hasRole, normalizeRole } from "./auth.utils";
import { ROLES, type Role } from "./roles";
import { useAuthorization } from "./useAuthorization";

export { ROLES, type Role, hasRole, hasAnyRole, hasAllRoles, normalizeRole, useAuthorization };

export const APP_ROLES = Object.values(ROLES);

export type AppRole = Role;

export const POS_WORKSPACE_ROLES: AppRole[] = [
    "ADMIN",
    "MANAGER",
    "SALE_STAFF"
];

export const BACKOFFICE_ROLES: AppRole[] = [
    "SUPER_ADMIN",
    "ADMIN",
    "MANAGER",
    "INVENTORY_STAFF",
    "QC",
    "ACCOUNTANT"
];

export const ORDER_ROLES: AppRole[] = [
    "ADMIN",
    "MANAGER",
    "ACCOUNTANT",
    "SALE_STAFF"
];

export const PRODUCT_ROLES: AppRole[] = [
    "ADMIN",
    "MANAGER",
    "INVENTORY_STAFF"
];

export const MANUFACTURING_ROLES: AppRole[] = [
    "ADMIN",
    "MANAGER",
    "INVENTORY_STAFF",
    "QC"
];

export const INVENTORY_ROLES: AppRole[] = [
    "ADMIN",
    "MANAGER",
    "INVENTORY_STAFF",
    "QC",
    "ACCOUNTANT"
];

export const INVENTORY_OPERATION_ROLES: AppRole[] = [
    "ADMIN",
    "MANAGER",
    "INVENTORY_STAFF",
    "QC"
];

export const MARKETING_ROLES: AppRole[] = [
    "ADMIN",
    "MANAGER"
];

export const REPORT_ROLES: AppRole[] = [
    "ADMIN",
    "MANAGER",
    "ACCOUNTANT"
];

export const SETTINGS_ROLES: AppRole[] = [ROLES.SUPER_ADMIN, ROLES.ADMIN];

/** Nhật ký hoạt động người dùng: chỉ quản trị viên. */
export const ACTION_LOG_ROLES: AppRole[] = [ROLES.SUPER_ADMIN, ROLES.ADMIN];

/** Cấu hình số lượng có thể bán và ngưỡng sản xuất: chỉ SUPER_ADMIN. */
export const SELLABLE_CONFIG_ROLES: AppRole[] = [ROLES.SUPER_ADMIN];

export const IMPORT_MANAGEMENT_ROLES: AppRole[] = [
    "ADMIN",
    "MANAGER"
];

const DEFAULT_ROUTE_BY_ROLE: Record<string, string> = {
    [ROLES.SUPER_ADMIN]: "/system/dashboard",
    [ROLES.ADMIN]: "/system/inventory/balances",
    [ROLES.MANAGER]: "/system/orders",
    [ROLES.INVENTORY_STAFF]: "/system/inventory/balances",
    [ROLES.QC]: "/system/inventory/quality",
    [ROLES.ACCOUNTANT]: "/system/orders/invoices",
    [ROLES.SALE_STAFF]: "/pos/sales",
    [ROLES.DELIVERY]: "/pos/sales",
    [ROLES.CUSTOMER]: "/"
};

const ROLE_PRIORITY: Role[] = [
    ROLES.ADMIN,
    ROLES.SUPER_ADMIN,
    ROLES.MANAGER,
    ROLES.QC,
    ROLES.ACCOUNTANT,
    ROLES.INVENTORY_STAFF,
    ROLES.SALE_STAFF,
    ROLES.DELIVERY,
    ROLES.CUSTOMER
];

export function getDefaultRouteForRoles(
    userRoles: readonly string[] | undefined
) {
    if (!userRoles || userRoles.length === 0) {
        return DEFAULT_ROUTE_BY_ROLE[ROLES.SALE_STAFF];
    }

    const normalizedUserRoles = new Set(userRoles.map(normalizeRole));
    const matchedRole = ROLE_PRIORITY.find((role) =>
        normalizedUserRoles.has(role)
    );

    return matchedRole
        ? DEFAULT_ROUTE_BY_ROLE[matchedRole]
        : DEFAULT_ROUTE_BY_ROLE[ROLES.SALE_STAFF];
}
