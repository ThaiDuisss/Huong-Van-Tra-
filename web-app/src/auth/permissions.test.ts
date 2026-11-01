import { describe, expect, it, vi } from "vitest";

vi.mock("../api/api-client", () => ({ apiClient: { get: vi.fn() } }));

import { apiClient } from "../api/api-client";
import { PERMISSIONS, hasAnyPermission, hasPermission, permissionApi } from "./permissions";

describe("hasPermission", () => {
    it("is true only when the loaded list contains the permission", () => {
        expect(hasPermission(["order:view", "order:create"], PERMISSIONS.ORDER_CREATE)).toBe(true);
        expect(hasPermission(["order:view"], PERMISSIONS.ORDER_CREATE)).toBe(false);
    });

    it("is false while permissions are not loaded", () => {
        expect(hasPermission(undefined, PERMISSIONS.ORDER_VIEW)).toBe(false);
        expect(hasPermission([], PERMISSIONS.ORDER_VIEW)).toBe(false);
    });
});

describe("hasAnyPermission", () => {
    it("allows when nothing is required", () => {
        expect(hasAnyPermission(undefined, undefined)).toBe(true);
        expect(hasAnyPermission(["order:view"], [])).toBe(true);
    });

    it("returns undefined (unknown) when the list could not be loaded, so callers fall back to roles", () => {
        expect(hasAnyPermission(undefined, ["user:manage"])).toBeUndefined();
    });

    it("needs one of the required permissions once the list is loaded", () => {
        expect(hasAnyPermission(["role:manage"], ["permission:manage", "role:manage"])).toBe(true);
        expect(hasAnyPermission(["order:view"], ["permission:manage", "role:manage"])).toBe(false);
        expect(hasAnyPermission([], ["permission:manage"])).toBe(false);
    });
});

describe("permission names and API", () => {
    it("uses the module:action format for every known permission", () => {
        for (const name of Object.values(PERMISSIONS)) {
            expect(name).toMatch(/^[a-z]+:[a-z]+$/);
        }
        expect(new Set(Object.values(PERMISSIONS)).size).toBe(Object.values(PERMISSIONS).length);
    });

    it("reads the current user's permissions from the identity endpoint", () => {
        permissionApi.myPermissions();
        expect(apiClient.get).toHaveBeenCalledWith("/identity/me/permissions");
    });
});
