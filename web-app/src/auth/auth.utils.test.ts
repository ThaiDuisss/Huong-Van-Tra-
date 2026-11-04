import { describe, expect, it } from "vitest";

import { hasAllRoles, hasAnyRole, hasRole, normalizeRole } from "./auth.utils";
import { ROLES } from "./roles";
import { getDefaultRouteForRoles } from "./role-utils";

describe("normalizeRole", () => {
    it("adds the ROLE_ prefix, trims and upper-cases", () => {
        expect(normalizeRole("admin")).toBe(ROLES.SUPER_ADMIN);
        expect(normalizeRole("  sale_staff ")).toBe(ROLES.SALE_STAFF);
        expect(normalizeRole("ROLE_MANAGER")).toBe(ROLES.MANAGER);
        expect(normalizeRole("role_qc")).toBe(ROLES.QC);
    });

    it("maps legacy role names to the current ones", () => {
        expect(normalizeRole("WAREHOUSE_STAFF")).toBe(ROLES.INVENTORY_STAFF);
        expect(normalizeRole("ROLE_WAREHOUSE")).toBe(ROLES.INVENTORY_STAFF);
        expect(normalizeRole("qc_staff")).toBe(ROLES.QC);
        expect(normalizeRole("SHIPPER")).toBe("ROLE_SHIPPER");
        expect(normalizeRole("superadmin")).toBe(ROLES.SUPER_ADMIN);
    });

    it("keeps unknown roles with the ROLE_ prefix instead of dropping them", () => {
        expect(normalizeRole("offline_access")).toBe("ROLE_OFFLINE_ACCESS");
        expect(normalizeRole("ROLE_UMA_AUTHORIZATION")).toBe("ROLE_UMA_AUTHORIZATION");
    });
});

describe("hasRole / hasAnyRole / hasAllRoles", () => {
    const keycloakRoles = ["ROLE_MANAGER", "offline_access", "qc_staff"];

    it("hasRole compares normalized names on both sides", () => {
        expect(hasRole(keycloakRoles, "MANAGER")).toBe(true);
        expect(hasRole(keycloakRoles, ROLES.QC)).toBe(true);
        expect(hasRole(keycloakRoles, "ADMIN")).toBe(false);
    });

    it("hasRole is false for missing or empty user roles", () => {
        expect(hasRole(undefined, "ADMIN")).toBe(false);
        expect(hasRole([], "ADMIN")).toBe(false);
    });

    it("hasAnyRole allows everyone when no role is required", () => {
        expect(hasAnyRole(undefined, undefined)).toBe(true);
        expect(hasAnyRole([], [])).toBe(true);
    });

    it("hasAnyRole needs at least one match", () => {
        expect(hasAnyRole(keycloakRoles, ["ADMIN", "QC"])).toBe(true);
        expect(hasAnyRole(keycloakRoles, ["ADMIN", "SALE_STAFF"])).toBe(false);
        expect(hasAnyRole(undefined, ["ADMIN"])).toBe(false);
    });

    it("hasAllRoles needs every required role", () => {
        expect(hasAllRoles(keycloakRoles, ["MANAGER", "QC"])).toBe(true);
        expect(hasAllRoles(keycloakRoles, ["MANAGER", "ADMIN"])).toBe(false);
        expect(hasAllRoles([], ["MANAGER"])).toBe(false);
        expect(hasAllRoles([], [])).toBe(true);
    });
});

describe("getDefaultRouteForRoles", () => {
    it("sends each role to its landing page", () => {
        expect(getDefaultRouteForRoles(["SUPER_ADMIN"])).toBe("/system/dashboard");
        expect(getDefaultRouteForRoles(["MANAGER"])).toBe("/system/orders");
        expect(getDefaultRouteForRoles(["QC"])).toBe("/management/inventory/disposals");
        expect(getDefaultRouteForRoles(["INVENTORY_STAFF"])).toBe("/system/inventory/warehouse");
        expect(getDefaultRouteForRoles(["SALE_STAFF"])).toBe("/pos/sales");
    });

    it("uses the highest-priority role when a user has several", () => {
        expect(getDefaultRouteForRoles(["SALE_STAFF", "MANAGER"])).toBe("/system/orders");
        expect(getDefaultRouteForRoles(["ROLE_SUPER_ADMIN", "ROLE_ADMIN"])).toBe("/system/dashboard");
        expect(getDefaultRouteForRoles(["sale_staff", "qc_staff"])).toBe("/management/inventory/disposals");
    });

    it("falls back to the POS for missing or unknown roles", () => {
        expect(getDefaultRouteForRoles(undefined)).toBe("/pos/sales");
        expect(getDefaultRouteForRoles([])).toBe("/pos/sales");
        expect(getDefaultRouteForRoles(["offline_access"])).toBe("/pos/sales");
    });
});
