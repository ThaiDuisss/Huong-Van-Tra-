import { describe, expect, it } from "vitest";

import { filterPosNavigationByRoles, posNavigationItems } from "./Pos/pos-navigation";
import {
  filterNavigationByRoles,
  filterSystemNavigationByRoles,
  findNavigationTrail,
  getFirstNavigationPath,
  isNavigationItemActive,
  systemNavigationItems,
  type SystemNavigationItem,
} from "./System/system-navigation";

function keys(items: readonly SystemNavigationItem[]) {
  return items.map((item) => item.key);
}

describe("system navigation by role and permission", () => {
  it("shows only the AI report menu to a cashier", () => {
    expect(keys(filterSystemNavigationByRoles(["ROLE_SALE_STAFF"]))).toEqual(["ai"]);
  });

  it("shows accounts and action logs to ADMIN but not to MANAGER", () => {
    const admin = keys(filterSystemNavigationByRoles(["ROLE_ADMIN"]));
    const manager = keys(filterSystemNavigationByRoles(["ROLE_MANAGER"]));

    expect(admin).toEqual(expect.arrayContaining(["accounts", "action-logs", "settings"]));
    expect(manager).not.toContain("accounts");
    expect(manager).not.toContain("action-logs");
    expect(manager).toContain("orders");
  });

  it("shows every menu to SUPER_ADMIN regardless of permissions", () => {
    const visible = filterSystemNavigationByRoles(["ROLE_SUPER_ADMIN"], []);
    expect(keys(visible)).toEqual(keys(systemNavigationItems));
  });

  it("lets loaded permissions decide over roles for items that declare permissions", () => {
    const withPermission = filterSystemNavigationByRoles(["ROLE_MANAGER"], ["role:manage"]);
    const withoutPermission = filterSystemNavigationByRoles(["ROLE_ADMIN"], ["order:view"]);

    expect(keys(withPermission)).toContain("settings");
    expect(keys(withoutPermission)).not.toContain("settings");
  });

  it("falls back to roles when the permission list is empty (not bootstrapped)", () => {
    expect(keys(filterSystemNavigationByRoles(["ROLE_ADMIN"], []))).toContain("settings");
  });

  it("drops a group whose children are all hidden", () => {
    const items: SystemNavigationItem[] = [
      {
        key: "group",
        label: "Group",
        icon: systemNavigationItems[0].icon,
        children: [{ key: "child", label: "Child", icon: systemNavigationItems[0].icon, to: "/x", roles: ["ADMIN"] }],
      },
    ];
    expect(filterNavigationByRoles(items, ["ROLE_QC"])).toEqual([]);
    expect(keys(filterNavigationByRoles(items, ["ROLE_ADMIN"]))).toEqual(["group"]);
  });
});

describe("system navigation paths", () => {
  const settings = systemNavigationItems.find((item) => item.key === "settings")!;
  const orders = systemNavigationItems.find((item) => item.key === "orders")!;

  it("finds the first reachable path of a group", () => {
    expect(getFirstNavigationPath(settings)).toBe("/system/authorization");
    expect(getFirstNavigationPath(orders)).toBe("/system/orders");
  });

  it("marks a group active for nested paths but not for similar prefixes", () => {
    expect(isNavigationItemActive(settings, "/system/authorization/roles")).toBe(true);
    expect(isNavigationItemActive(settings, "/system/authorizationx")).toBe(false);
  });

  it("builds the breadcrumb trail for page and sub-page paths", () => {
    expect(findNavigationTrail(systemNavigationItems, "/system/accounts")?.section.key).toBe("accounts");
    const invoice = findNavigationTrail(systemNavigationItems, "/system/orders/invoices/abc");
    expect(invoice?.section.key).toBe("orders");
    expect(invoice?.page?.key).toBe("orders-invoices");
    expect(findNavigationTrail(systemNavigationItems, "/unknown")).toBeUndefined();
  });
});

describe("POS navigation", () => {
  it("shows the POS to cashiers and hides it from inventory staff", () => {
    const cashier = filterPosNavigationByRoles(posNavigationItems, ["ROLE_SALE_STAFF"]).map((item) => item.key);
    const inventory = filterPosNavigationByRoles(posNavigationItems, ["ROLE_INVENTORY_STAFF"]).map((item) => item.key);

    expect(cashier).toContain("pos");
    expect(inventory).not.toContain("pos");
  });

  it("shows nothing without roles when every item requires one", () => {
    const visible = filterPosNavigationByRoles(posNavigationItems, []);
    expect(visible.every((item) => !item.roles || item.roles.length === 0)).toBe(true);
  });
});
