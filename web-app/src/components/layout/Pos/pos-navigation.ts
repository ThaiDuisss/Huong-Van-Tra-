import {
  BarChart3,
  BriefcaseBusiness,
  FileText,
  History,
  Leaf,
  PackageOpen,
  PackageSearch,
  ShoppingBag,
  Sparkles,
  User,
  WalletCards
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import {
  AI_REPORT_ROLES,
  POS_WORKSPACE_ROLES,
  type AppRole,
  hasAnyRole
} from "../../../auth/role-utils";
import { routePaths } from "../../../routes/path";
import { getOrderReturnAccess } from "../../../auth/order-return-access";

export interface PosNavigationItem {
  key: string;
  label: string;
  icon: LucideIcon;
  to?: string;
  roles?: AppRole[];
  returnAccess?: "canReadSales";
  children?: PosNavigationItem[];
}

export const posNavigationItems: PosNavigationItem[] = [
  {
    key: "pos",
    label: "POS",
    icon: BriefcaseBusiness,
    to: routePaths.posSales,
    roles: POS_WORKSPACE_ROLES
  },
  {
    key: "shelf-replenishment",
    label: "Phiếu nhập kệ",
    icon: PackageOpen,
    to: routePaths.posShelfReplenishments,
    roles: POS_WORKSPACE_ROLES
  },
  {
    key: "orders",
    label: "Đơn hàng",
    icon: ShoppingBag,
    roles: POS_WORKSPACE_ROLES,
    children: [
      {
        key: "sales-history",
        label: "Bán hàng hôm nay",
        icon: BarChart3,
        to: routePaths.salesHistory,
        roles: POS_WORKSPACE_ROLES
      },
      {
        key: "orders-returns",
        label: "Hoàn hàng",
        icon: History,
        to: routePaths.returns,
        roles: POS_WORKSPACE_ROLES,
        returnAccess: "canReadSales"
      },
      {
        key: "orders-refunds",
        label: "Hoàn tiền",
        icon: WalletCards,
        to: routePaths.refunds,
        roles: POS_WORKSPACE_ROLES,
        returnAccess: "canReadSales"
      },
      {
        key: "backorders",
        label: "Đơn đang đợi hàng",
        icon: PackageSearch,
        to: routePaths.posBackorders,
        roles: POS_WORKSPACE_ROLES
      }
    ]
  },
  {
    key: "customers",
    label: "Khách hàng",
    icon: User,
    to: routePaths.posCustomers,
    roles: POS_WORKSPACE_ROLES
  },
  {
    key: "ai",
    label: "AI",
    icon: Sparkles,
    roles: AI_REPORT_ROLES,
    children: [
      {
        key: "ai-reports",
        label: "Báo cáo AI",
        icon: FileText,
        to: routePaths.aiReports,
        roles: AI_REPORT_ROLES
      }
    ]
  }
];

export function filterPosNavigationByRoles(
  items: readonly PosNavigationItem[],
  userRoles: readonly string[] | undefined,
  permissions?: readonly string[]
): PosNavigationItem[] {
  return items
    .filter((item) => hasAnyRole(userRoles, item.roles))
    .filter((item) => !item.returnAccess || getOrderReturnAccess(userRoles, permissions)[item.returnAccess])
    .map((item) => ({
      ...item,
      children: item.children
        ? filterPosNavigationByRoles(item.children, userRoles, permissions)
        : undefined
    }))
    .filter((item) => !item.children || item.children.length > 0);
}
