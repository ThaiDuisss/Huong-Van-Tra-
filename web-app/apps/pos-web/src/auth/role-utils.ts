import { hasAllRoles, hasAnyRole, hasRole, normalizeRole } from "./auth.utils";
import { ROLES, type Role } from "./roles";
import { useAuthorization } from "./useAuthorization";

export { ROLES, type Role, hasRole, hasAnyRole, hasAllRoles, normalizeRole, useAuthorization };

export const APP_ROLES = Object.values(ROLES);

export type AppRole = Role;

export const POS_WORKSPACE_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "POS_STAFF"
];

export const BACKOFFICE_ROLES: AppRole[] = [
    "SUPER_ADMIN",
    "ADMIN",
    "BRANCH_MANAGER",
    "INVENTORY_MANAGER",
    "INVENTORY_STAFF",
    "QC",
    "ACCOUNTANT"
];

export const ORDER_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "ACCOUNTANT",
    "POS_STAFF"
];

export const PRODUCT_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "INVENTORY_MANAGER",
    "INVENTORY_STAFF"
];

export const MANUFACTURING_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "INVENTORY_MANAGER",
    "INVENTORY_STAFF",
    "QC"
];

export const INVENTORY_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "INVENTORY_MANAGER",
    "INVENTORY_STAFF",
    "QC",
    "ACCOUNTANT"
];

export const INVENTORY_OPERATION_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "INVENTORY_MANAGER",
    "INVENTORY_STAFF",
    "QC"
];

export const MARKETING_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER"
];

export const REPORT_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "INVENTORY_MANAGER",
    "ACCOUNTANT"
];

export const SETTINGS_ROLES: AppRole[] = [ROLES.SUPER_ADMIN, ROLES.ADMIN];

/** Nhật ký hoạt động người dùng: chỉ quản trị viên. */
export const ACTION_LOG_ROLES: AppRole[] = [ROLES.SUPER_ADMIN, ROLES.ADMIN];

const DEFAULT_ROUTE_BY_ROLE: Record<string, string> = {
    [ROLES.SUPER_ADMIN]: "/system/dashboard",
    [ROLES.ADMIN]: "/system/inventory/balances",
    [ROLES.BRANCH_MANAGER]: "/system/orders",
    [ROLES.INVENTORY_MANAGER]: "/system/inventory/balances",
    [ROLES.INVENTORY_STAFF]: "/system/inventory/balances",
    [ROLES.QC]: "/system/inventory/quality",
    [ROLES.ACCOUNTANT]: "/system/orders/invoices",
    [ROLES.POS_STAFF]: "/pos/sales",
    [ROLES.DELIVERY]: "/pos/sales",
    [ROLES.CUSTOMER]: "/"
};

const ROLE_PRIORITY: Role[] = [
    ROLES.ADMIN,
    ROLES.SUPER_ADMIN,
    ROLES.BRANCH_MANAGER,
    ROLES.INVENTORY_MANAGER,
    ROLES.QC,
    ROLES.ACCOUNTANT,
    ROLES.INVENTORY_STAFF,
    ROLES.POS_STAFF,
    ROLES.DELIVERY,
    ROLES.CUSTOMER
];

export function getDefaultRouteForRoles(
    userRoles: readonly string[] | undefined
) {
    if (!userRoles || userRoles.length === 0) {
        return DEFAULT_ROUTE_BY_ROLE[ROLES.POS_STAFF];
    }

    const normalizedUserRoles = new Set(userRoles.map(normalizeRole));
    const matchedRole = ROLE_PRIORITY.find((role) =>
        normalizedUserRoles.has(role)
    );

    return matchedRole
        ? DEFAULT_ROUTE_BY_ROLE[matchedRole]
        : DEFAULT_ROUTE_BY_ROLE[ROLES.POS_STAFF];
}
