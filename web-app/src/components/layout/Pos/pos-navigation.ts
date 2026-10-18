import {
  BarChart3,
  BriefcaseBusiness,
  Clock3,
  FileClock,
  FileText,
  PackageSearch,
  ShoppingBag,
  User
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import {
  POS_WORKSPACE_ROLES,
  type AppRole,
  hasAnyRole
} from "../../../auth/role-utils";
import { routePaths } from "../../../routes/path";

export interface PosNavigationItem {
  key: string;
  label: string;
  icon: LucideIcon;
  to?: string;
  roles?: AppRole[];
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
    key: "orders",
    label: "Đơn hàng",
    icon: ShoppingBag,
    roles: POS_WORKSPACE_ROLES,
    children: [
      {
        key: "held-orders",
        label: "Đơn đang giữ",
        icon: FileClock,
        roles: POS_WORKSPACE_ROLES
      },
      {
        key: "shift-orders",
        label: "Đơn trong ca",
        icon: FileText,
        roles: POS_WORKSPACE_ROLES
      },
      {
        key: "backorders",
        label: "Đơn chờ hàng",
        icon: PackageSearch,
        to: routePaths.posBackorders,
        roles: POS_WORKSPACE_ROLES
      },
      {
        key: "sales-history",
        label: "Bán hàng hôm nay",
        icon: BarChart3,
        to: routePaths.salesHistory,
        roles: POS_WORKSPACE_ROLES
      }
    ]
  },
  {
    key: "shift",
    label: "Ca làm việc",
    icon: Clock3,
    roles: POS_WORKSPACE_ROLES
  },
  {
    key: "customers",
    label: "Khách hàng",
    icon: User,
    to: routePaths.customers,
    roles: POS_WORKSPACE_ROLES
  }
];

export function filterPosNavigationByRoles(
  items: readonly PosNavigationItem[],
  userRoles: readonly string[] | undefined
): PosNavigationItem[] {
  return items
    .filter((item) => hasAnyRole(userRoles, item.roles))
    .map((item) => ({
      ...item,
      children: item.children
        ? filterPosNavigationByRoles(item.children, userRoles)
        : undefined
    }))
    .filter((item) => !item.children || item.children.length > 0);
}
