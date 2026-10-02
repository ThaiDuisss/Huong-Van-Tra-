import {
  INVENTORY_ROLES,
  MANUFACTURING_ROLES,
  ORDER_ROLES,
  REPORT_ROLES,
  hasAnyRole,
  type AppRole,
} from "../../../auth/role-utils";
import type { DashboardTab } from "../types/dashboard.types";

/**
 * Số liệu tài chính (doanh thu, AOV, tiền chờ thu, giá trị tồn) — giao của
 * ORDER_ROLES và REPORT_ROLES hiện có. Không tạo role mới.
 */
export const DASHBOARD_FINANCE_ROLES: AppRole[] = ["ADMIN", "MANAGER", "ACCOUNTANT"];

export interface DashboardCapabilities {
  sales: boolean;
  finance: boolean;
  inventory: boolean;
  inventoryValue: boolean;
  production: boolean;
  alerts: boolean;
}

export function getDashboardCapabilities(
  roles: readonly string[] | undefined,
): DashboardCapabilities {
  const sales = hasAnyRole(roles, ORDER_ROLES);
  const inventory = hasAnyRole(roles, INVENTORY_ROLES);

  return {
    alerts: inventory,
    finance: sales && hasAnyRole(roles, DASHBOARD_FINANCE_ROLES),
    inventory,
    inventoryValue: inventory && hasAnyRole(roles, REPORT_ROLES),
    production: hasAnyRole(roles, MANUFACTURING_ROLES),
    sales,
  };
}

export function getVisibleDashboardTabs(capabilities: DashboardCapabilities): DashboardTab[] {
  const tabs: DashboardTab[] = ["OVERVIEW"];

  if (capabilities.sales) tabs.push("POS");
  if (capabilities.inventory) tabs.push("INVENTORY");
  if (capabilities.production) tabs.push("PRODUCTION");
  if (capabilities.alerts) tabs.push("ALERTS");

  return tabs;
}
