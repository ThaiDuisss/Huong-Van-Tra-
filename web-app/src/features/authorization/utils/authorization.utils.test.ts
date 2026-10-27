import { describe, expect, it } from "vitest";

import type { PermissionResponse } from "../types/authorization.types";
import {
  canUse,
  diffSelection,
  filterPermissions,
  groupPermissionsByModule,
  hasSelectionChanged,
  isValidPermissionName,
  validatePermissionForm,
} from "./authorization.utils";

function permission(name: string, displayName = name, description = ""): PermissionResponse {
  return { assignedRoles: [], builtIn: false, description, displayName, id: name, name };
}

describe("permission name validation", () => {
  it("accepts module:action in lower case, digits and dashes", () => {
    expect(isValidPermissionName("order:create")).toBe(true);
    expect(isValidPermissionName(" Inventory:Receive-Goods2 ")).toBe(true);
  });

  it("rejects names without a module, with spaces or special characters", () => {
    expect(isValidPermissionName("ordercreate")).toBe(false);
    expect(isValidPermissionName("order create")).toBe(false);
    expect(isValidPermissionName("order:")).toBe(false);
    expect(isValidPermissionName(":create")).toBe(false);
    expect(isValidPermissionName("order:create:now")).toBe(false);
    expect(isValidPermissionName("a".repeat(70) + ":x")).toBe(false);
  });

  it("validates the form for create and edit", () => {
    expect(validatePermissionForm({ name: "", displayName: "X", isEdit: false })).toMatch(/tên quyền/i);
    expect(validatePermissionForm({ name: "bad name", displayName: "X", isEdit: false })).toMatch(/module:action/);
    expect(validatePermissionForm({ name: "order:x", displayName: "  ", isEdit: false })).toMatch(/hiển thị/);
    expect(validatePermissionForm({ displayName: "Ok", description: "d".repeat(256), isEdit: true })).toMatch(/255/);
    expect(validatePermissionForm({ displayName: "Ok", isEdit: true })).toBeNull();
    expect(validatePermissionForm({ name: "order:x", displayName: "Ok", isEdit: false })).toBeNull();
  });
});

describe("selection diff", () => {
  it("computes additions and removals only", () => {
    expect(diffSelection(["order:view", "order:create"], ["order:create", "report:view"])).toEqual({
      added: ["report:view"],
      removed: ["order:view"],
    });
    expect(hasSelectionChanged(["a:b"], ["a:b"])).toBe(false);
    expect(hasSelectionChanged(["a:b"], [])).toBe(true);
  });
});

describe("grouping and filtering", () => {
  it("groups by module with labels and sorted names", () => {
    const groups = groupPermissionsByModule([
      permission("order:view"),
      permission("inventory:view"),
      permission("order:create"),
    ]);
    expect(groups.map((group) => group.module)).toEqual(["inventory", "order"]);
    expect(groups[1]?.label).toBe("Đơn hàng");
    expect(groups[1]?.items.map((item) => item.name)).toEqual(["order:create", "order:view"]);
  });

  it("filters by name, display name and description", () => {
    const list = [permission("order:view", "Xem đơn", "Danh sách đơn"), permission("report:view", "Báo cáo")];
    expect(filterPermissions(list, "xem").map((item) => item.name)).toEqual(["order:view"]);
    expect(filterPermissions(list, "danh sách").map((item) => item.name)).toEqual(["order:view"]);
    expect(filterPermissions(list, "report").map((item) => item.name)).toEqual(["report:view"]);
    expect(filterPermissions(list, "  ")).toHaveLength(2);
  });
});

describe("permission-aware actions", () => {
  it("uses permissions when loaded and ignores roles", () => {
    expect(canUse({ permissions: ["permission:manage"], roles: ["SALE_STAFF"] }, ["permission:manage"], ["ADMIN"])).toBe(true);
    expect(canUse({ permissions: ["order:view"], roles: ["ADMIN"] }, ["permission:manage"], ["ADMIN"])).toBe(false);
  });

  it("treats an empty permission list as not bootstrapped and falls back to roles", () => {
    expect(canUse({ permissions: [], roles: ["ADMIN"] }, ["permission:manage"], ["ADMIN"])).toBe(true);
    expect(canUse({ permissions: [], roles: ["SALE_STAFF"] }, ["permission:manage"], ["ADMIN"])).toBe(false);
  });

  it("always allows SUPER_ADMIN like the backend break-glass rule", () => {
    expect(canUse({ permissions: [], roles: ["ROLE_SUPER_ADMIN"] }, ["permission:manage"], [])).toBe(true);
    expect(canUse({ permissions: ["order:view"], roles: ["superadmin"] }, ["permission:manage"], [])).toBe(true);
  });

  it("falls back to roles when permissions are unknown", () => {
    expect(canUse({ permissions: undefined, roles: ["ROLE_ADMIN"] }, ["permission:manage"], ["ADMIN"])).toBe(true);
    expect(canUse({ permissions: undefined, roles: ["SALE_STAFF"] }, ["permission:manage"], ["ADMIN"])).toBe(false);
  });
});
