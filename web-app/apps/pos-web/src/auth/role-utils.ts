export const APP_ROLES = [
    "ADMIN",
    "BRANCH_MANAGER",
    "POS_STAFF",
    "WAREHOUSE_STAFF",
    "INVENTORY_MANAGER",
    "ACCOUNTANT",
    "SHIPPER"
] as const;

export type AppRole = (typeof APP_ROLES)[number];

export const POS_WORKSPACE_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "POS_STAFF"
];

export const BACKOFFICE_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "WAREHOUSE_STAFF",
    "INVENTORY_MANAGER",
    "ACCOUNTANT"
];

export const ORDER_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "ACCOUNTANT"
];

export const PRODUCT_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "WAREHOUSE_STAFF",
    "INVENTORY_MANAGER"
];

export const MANUFACTURING_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "WAREHOUSE_STAFF",
    "INVENTORY_MANAGER"
];

export const INVENTORY_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "WAREHOUSE_STAFF",
    "INVENTORY_MANAGER",
    "ACCOUNTANT"
];

export const INVENTORY_OPERATION_ROLES: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "WAREHOUSE_STAFF",
    "INVENTORY_MANAGER"
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

export const SETTINGS_ROLES: AppRole[] = ["ADMIN"];

const DEFAULT_ROUTE_BY_ROLE: Record<AppRole, string> = {
    ADMIN: "/system/inventory/balances",
    BRANCH_MANAGER: "/system/orders",
    INVENTORY_MANAGER: "/system/inventory/balances",
    ACCOUNTANT: "/system/orders/invoices",
    WAREHOUSE_STAFF: "/system/inventory/balances",
    POS_STAFF: "/pos/sales",
    SHIPPER: "/pos/sales"
};

const ROLE_PRIORITY: AppRole[] = [
    "ADMIN",
    "BRANCH_MANAGER",
    "INVENTORY_MANAGER",
    "ACCOUNTANT",
    "WAREHOUSE_STAFF",
    "POS_STAFF",
    "SHIPPER"
];

export function normalizeRole(role: string) {
    return role.replace(/^ROLE_/i, "").trim().toUpperCase();
}

export function hasAnyRole(
    userRoles: readonly string[] | undefined,
    allowedRoles?: readonly string[]
) {
    if (!allowedRoles || allowedRoles.length === 0) {
        return true;
    }

    if (!userRoles || userRoles.length === 0) {
        return false;
    }

    const normalizedUserRoles = new Set(userRoles.map(normalizeRole));

    return allowedRoles.some((role) =>
        normalizedUserRoles.has(normalizeRole(role))
    );
}

export function getDefaultRouteForRoles(
    userRoles: readonly string[] | undefined
) {
    if (!userRoles || userRoles.length === 0) {
        return DEFAULT_ROUTE_BY_ROLE.POS_STAFF;
    }

    const normalizedUserRoles = new Set(userRoles.map(normalizeRole));
    const matchedRole = ROLE_PRIORITY.find((role) =>
        normalizedUserRoles.has(role)
    );

    return matchedRole
        ? DEFAULT_ROUTE_BY_ROLE[matchedRole]
        : DEFAULT_ROUTE_BY_ROLE.POS_STAFF;
}
