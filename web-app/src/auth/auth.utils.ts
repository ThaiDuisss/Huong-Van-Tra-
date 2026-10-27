import { ROLES, type Role } from "./roles";

export function normalizeRole(role: string): Role {
    const raw = role.trim().toUpperCase();
    const withoutPrefix = raw.replace(/^ROLE_/i, "");

    if (withoutPrefix === "WAREHOUSE_STAFF" || withoutPrefix === "WAREHOUSE") {
        return ROLES.INVENTORY_STAFF;
    }
    if (withoutPrefix === "QC_STAFF") {
        return ROLES.QC;
    }
    if (withoutPrefix === "SHIPPER") {
        return ROLES.DELIVERY;
    }
    if (withoutPrefix === "SUPERADMIN") {
        return ROLES.SUPER_ADMIN;
    }

    const key = withoutPrefix as keyof typeof ROLES;
    if (key in ROLES) {
        return ROLES[key];
    }

    return (raw.startsWith("ROLE_") ? raw : `ROLE_${raw}`) as Role;
}

export function hasRole(
    userRoles: readonly string[] | undefined,
    role: Role | string
): boolean {
    if (!userRoles || userRoles.length === 0) {
        return false;
    }
    const normalizedTarget = normalizeRole(role);
    return userRoles.some((r) => normalizeRole(r) === normalizedTarget);
}

export function hasAnyRole(
    userRoles: readonly string[] | undefined,
    roles?: readonly (Role | string)[]
): boolean {
    if (!roles || roles.length === 0) {
        return true;
    }
    if (!userRoles || userRoles.length === 0) {
        return false;
    }
    const normalizedUserRoles = new Set(userRoles.map(normalizeRole));
    return roles.some((role) => normalizedUserRoles.has(normalizeRole(role)));
}

export function hasAllRoles(
    userRoles: readonly string[] | undefined,
    roles?: readonly (Role | string)[]
): boolean {
    if (!roles || roles.length === 0) {
        return true;
    }
    if (!userRoles || userRoles.length === 0) {
        return false;
    }
    const normalizedUserRoles = new Set(userRoles.map(normalizeRole));
    return roles.every((role) => normalizedUserRoles.has(normalizeRole(role)));
}
