import {
  BarChart3,
  BadgePercent,
  Building2,
  Boxes,
  CalendarDays,
  ClipboardCheck,
  ClipboardList,
  FileText,
  FolderTree,
  History,
  Home,
  Layers3,
  MapPinned,
  Package,
  PackagePlus,
  Plane,
  RefreshCw,
  Ruler,
  ScrollText,
  Settings,
  ShoppingCart,
  SlidersHorizontal,
  Sparkles,
  Trash2,
  Truck,
  Upload,
  User,
  WalletCards,
  Warehouse,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import {
  ACTION_LOG_ROLES,
  AI_REPORT_ROLES,
  BACKOFFICE_ROLES,
  INVENTORY_OPERATION_ROLES,
  INVENTORY_ROLES,
  MANUFACTURING_ROLES,
  MARKETING_ROLES,
  ORDER_ROLES,
  PRODUCT_ROLES,
  REPORT_ROLES,
  SELLABLE_CONFIG_ROLES,
  SETTINGS_ROLES,
  type AppRole,
  hasAnyRole,
} from "../../../auth/role-utils";
import { routePaths } from "../../../routes/path";

export interface SystemNavigationItem {
  key: string;
  label: string;
  icon: LucideIcon;
  to?: string;
  roles?: AppRole[];
  children?: SystemNavigationItem[];
}

export interface NavigationTrail {
  section: SystemNavigationItem;
  page?: SystemNavigationItem;
  subPage?: SystemNavigationItem;
}

export const systemNavigationItems: SystemNavigationItem[] = [
  {
    key: "dashboard",
    label: "Tổng quan",
    icon: Home,
    to: routePaths.systemDashboard,
    roles: BACKOFFICE_ROLES,
  },
  {
    key: "orders",
    label: "Đơn hàng",
    icon: ShoppingCart,
    roles: ORDER_ROLES,
    children: [
      {
        key: "orders-all",
        label: "Tất cả đơn hàng",
        to: routePaths.orders,
        icon: FileText,
        roles: ORDER_ROLES,
      },
      {
        key: "orders-invoices",
        label: "Hóa đơn",
        to: routePaths.invoices,
        icon: FileText,
        roles: ORDER_ROLES,
      },
      {
        key: "orders-returns",
        label: "Hoàn hàng",
        to: routePaths.returns,
        icon: History,
        roles: ["ADMIN", "MANAGER"],
      },
      {
        key: "orders-refunds",
        label: "Hoàn tiền",
        to: routePaths.refunds,
        icon: WalletCards,
        roles: ["ADMIN", "MANAGER", "ACCOUNTANT"],
      },
    ],
  },
  {
    key: "catalog",
    label: "Hàng hóa",
    icon: Package,
    roles: PRODUCT_ROLES,
    children: [
      {
        key: "catalog-products",
        label: "Sản phẩm",
        to: routePaths.products,
        icon: Package,
        roles: PRODUCT_ROLES,
      },
      {
        key: "catalog-variants",
        label: "Mặt hàng",
        to: routePaths.productVariants,
        icon: Layers3,
        roles: PRODUCT_ROLES,
      },
      {
        key: "catalog-categories",
        label: "Danh mục sản phẩm",
        to: routePaths.categories,
        icon: FolderTree,
        roles: ["ADMIN", "MANAGER"],
      },
      {
        key: "catalog-units",
        label: "Đơn vị tính",
        to: routePaths.units,
        icon: Ruler,
        roles: ["ADMIN", "MANAGER"],
      },
    ],
  },
  {
    key: "import-excel",
    label: "Nhập Excel",
    icon: Upload,
    roles: PRODUCT_ROLES,
    children: [
      {
        key: "import-excel-requests",
        label: "Yêu cầu nhập",
        to: routePaths.importExcelRequests,
        icon: Upload,
        roles: PRODUCT_ROLES,
      },
    ],
  },
  {
    key: "manufacturing",
    label: "Sản xuất",
    icon: ClipboardList,
    roles: MANUFACTURING_ROLES,
    children: [
      {
        key: "manufacturing-production-orders",
        label: "Lệnh sản xuất",
        to: routePaths.inventoryProductionOrderList,
        icon: ClipboardList,
        roles: MANUFACTURING_ROLES,
      },
      {
        key: "manufacturing-custom-bundles",
        label: "Custom Tea Bundle",
        to: routePaths.customBundles,
        icon: Boxes,
        roles: MANUFACTURING_ROLES,
      },
      {
        key: "manufacturing-ingredients",
        label: "Nguyên liệu",
        to: routePaths.ingredient,
        icon: Layers3,
        roles: MANUFACTURING_ROLES,
      },
      {
        key: "manufacturing-suppliers",
        label: "Nhà cung cấp",
        to: routePaths.supplier,
        icon: Building2,
        roles: MANUFACTURING_ROLES,
      },
    ],
  },
  {
    key: "inventory",
    label: "Kho",
    icon: Warehouse,
    roles: INVENTORY_ROLES,
    children: [
      {
        key: "inventory-stock",
        label: "Tồn kho",
        icon: Warehouse,
        roles: INVENTORY_ROLES,
        children: [
          // {
          //   key: "inventory-balances",
          //   label: "Tồn kho hiện tại",
          //   icon: Warehouse,
          //   to: routePaths.inventoryBalances,
          //   roles: INVENTORY_ROLES,
          // },
          {
            key: "warehouse-inventory",
            label: "Tồn kho kho tổng",
            icon: Package,
            to: routePaths.warehouseInventory,
            roles: ["ADMIN", "MANAGER"],
          },
          {
            key: "inventory-reorder-policies",
            label: "Chính sách bổ sung",
            icon: Boxes,
            to: routePaths.inventoryReorderPolicies,
            roles: ["ADMIN", "MANAGER"],
          },
          {
            key: "inventory-transactions",
            label: "Lịch sử biến động",
            icon: History,
            to: routePaths.inventoryTransactions,
            roles: ["ADMIN", "MANAGER", "ACCOUNTANT"],
          },
        ],
      },
      {
        key: "inventory-inbound-outbound",
        label: "Nhập / Xuất",
        icon: FileText,
        roles: INVENTORY_ROLES,
        children: [
          {
            key: "inventory-receipts",
            label: "Nhập kho",
            icon: Boxes,
            to: routePaths.goodsReceipts,
            roles: INVENTORY_OPERATION_ROLES,
          },
          {
            key: "inventory-shelf-replenishments",
            label: "Nhập kệ",
            icon: PackagePlus,
            to: routePaths.shelfReplenishments,
            roles: INVENTORY_OPERATION_ROLES,
          },
          {
            key: "inventory-documents",
            label: "Chứng từ kho",
            icon: FileText,
            to: routePaths.inventoryDocuments,
            roles: INVENTORY_ROLES,
          },
        ],
      },
      {
        key: "inventory-transfers",
        label: "Chuyển kho",
        icon: Truck,
        to: routePaths.inventoryTransfers,
        roles: INVENTORY_OPERATION_ROLES,
      },
      {
        key: "inventory-counting",
        label: "Kiểm kê",
        icon: ClipboardCheck,
        roles: INVENTORY_OPERATION_ROLES,
        children: [
          {
            key: "inventory-stocktakes",
            label: "Kỳ kiểm kê",
            icon: ClipboardCheck,
            to: routePaths.inventoryStocktakes,
            roles: INVENTORY_OPERATION_ROLES,
          },
          {
            key: "inventory-adjustments",
            label: "Phiếu cập nhật tồn kho",
            icon: ClipboardList,
            to: routePaths.inventoryStocktakeAdjustments,
            roles: ["ADMIN", "MANAGER"],
          },
        ],
      },
      {
        key: "inventory-handling",
        label: "Xử lý hàng",
        icon: RefreshCw,
        roles: INVENTORY_OPERATION_ROLES,
        children: [
          {
            key: "inventory-customer-returns",
            label: "Khách trả hàng",
            icon: RefreshCw,
            to: routePaths.inventoryCustomerReturnList,
            roles: ["ADMIN", "MANAGER", "INVENTORY_STAFF"],
          },
          {
            key: "inventory-supplier-returns",
            label: "Trả nhà cung cấp",
            icon: RefreshCw,
            to: routePaths.inventorySupplierReturnList,
            roles: ["ADMIN", "MANAGER", "INVENTORY_STAFF"],
          },
          {
            key: "inventory-disposals",
            label: "Hỏng / tiêu hủy",
            icon: Trash2,
            to: routePaths.inventoryDisposals,
            roles: INVENTORY_OPERATION_ROLES,
          },
          {
            key: "inventory-quality",
            label: "Chất lượng hàng hóa",
            icon: ClipboardCheck,
            to: routePaths.inventoryQuality,
            roles: INVENTORY_OPERATION_ROLES,
          },
          {
            key: "inventory-alerts",
            label: "Cảnh báo tồn kho",
            icon: ClipboardList,
            to: routePaths.inventoryAlerts,
            roles: ["ADMIN", "MANAGER"],
          },
        ],
      },
      {
        key: "inventory-shelves",
        label: "Quản lý kệ",
        icon: MapPinned,
        to: routePaths.inventoryShelves,
        roles: ["ADMIN", "MANAGER"],
      },
    ],
  },
  {
    key: "customers",
    label: "Khách hàng",
    icon: User,
    roles: ["ADMIN", "MANAGER"],
    children: [
      {
        key: "customers-list",
        label: "Danh sách khách hàng",
        to: routePaths.customers,
        icon: User,
        roles: ["ADMIN", "MANAGER"],
      },
      {
        key: "customers-tiers",
        label: "Hạng khách hàng",
        to: routePaths.customerTiers,
        icon: BadgePercent,
        roles: ["ADMIN", "MANAGER"],
      },
    ],
  },
  {
    key: "marketing",
    label: "Marketing",
    icon: Plane,
    roles: MARKETING_ROLES,
    children: [
      {
        key: "marketing-events",
        label: "Sự kiện",
        to: routePaths.marketingEvents,
        icon: CalendarDays,
        roles: MARKETING_ROLES,
      },
      {
        key: "marketing-programs",
        label: "Chương trình giảm giá",
        to: routePaths.discountPrograms,
        icon: BadgePercent,
        roles: MARKETING_ROLES,
      },
      {
        key: "marketing-codes",
        label: "Mã giảm giá",
        to: routePaths.discountCodes,
        icon: BadgePercent,
        roles: MARKETING_ROLES,
      },
      {
        key: "marketing-history",
        label: "Lịch sử giảm giá",
        to: routePaths.discountHistory,
        icon: History,
        roles: MARKETING_ROLES,
      },
    ],
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
        to: routePaths.aiReports,
        icon: FileText,
        roles: AI_REPORT_ROLES,
      },
    ],
  },
  {
    key: "reports",
    label: "Báo cáo",
    icon: BarChart3,
    roles: REPORT_ROLES,
    children: [
      {
        key: "report-inventory-value",
        label: "Giá trị tồn kho",
        to: routePaths.reportInventoryValue,
        icon: BarChart3,
        roles: REPORT_ROLES,
      },
      {
        key: "report-turnover",
        label: "Hiệu suất luân chuyển",
        to: routePaths.reportTurnover,
        icon: RefreshCw,
        roles: REPORT_ROLES,
      },
      {
        key: "report-wastage",
        label: "Hao hụt sản xuất",
        to: routePaths.reportProductionWastage,
        icon: ClipboardList,
        roles: REPORT_ROLES,
      },
      {
        key: "report-disposal",
        label: "Hàng hỏng & Tiêu hủy",
        to: routePaths.reportDisposalSummary,
        icon: Trash2,
        roles: REPORT_ROLES,
      },
    ],
  },
  {
    key: "action-logs",
    label: "Nhật ký hoạt động",
    icon: ScrollText,
    to: routePaths.systemActionLogs,
    roles: ACTION_LOG_ROLES,
  },
  {
    key: "sellable-config",
    label: "Cấu hình bán hàng",
    icon: SlidersHorizontal,
    to: routePaths.systemSellableConfig,
    roles: SELLABLE_CONFIG_ROLES,
  },
  {
    key: "settings",
    label: "Cài đặt",
    icon: Settings,
    roles: SETTINGS_ROLES,
  },
];

export function filterNavigationByRoles(
  items: readonly SystemNavigationItem[],
  userRoles: readonly string[] | undefined,
): SystemNavigationItem[] {
  return items
    .filter((item) => hasAnyRole(userRoles, item.roles))
    .map((item) => {
      const children = item.children
        ? filterNavigationByRoles(item.children, userRoles)
        : undefined;

      return {
        ...item,
        children,
      };
    })
    .filter((item) => !item.children || item.children.length > 0);
}

/** Reflect both the shell gate and each menu's roles for this workspace. */
export function filterSystemNavigationByRoles(
  userRoles: readonly string[] | undefined,
): SystemNavigationItem[] {
  const items = hasAnyRole(userRoles, BACKOFFICE_ROLES)
    ? systemNavigationItems
    : systemNavigationItems.filter((item) => item.key === "ai");

  return filterNavigationByRoles(items, userRoles);
}

export function getFirstNavigationPath(item: SystemNavigationItem): string | undefined {
  if (item.to) {
    return item.to;
  }

  for (const child of item.children ?? []) {
    const childPath = getFirstNavigationPath(child);

    if (childPath) {
      return childPath;
    }
  }

  return undefined;
}

export function isNavigationItemActive(item: SystemNavigationItem, pathname: string): boolean {
  if (item.to && (pathname === item.to || pathname.startsWith(`${item.to}/`))) {
    return true;
  }

  return item.children?.some((child) => isNavigationItemActive(child, pathname)) ?? false;
}

export function findNavigationTrail(
  items: readonly SystemNavigationItem[],
  pathname: string,
): NavigationTrail | undefined {
  for (const section of items) {
    if (section.to && (pathname === section.to || pathname.startsWith(`${section.to}/`))) {
      return { section };
    }

    for (const page of section.children ?? []) {
      if (page.to && (pathname === page.to || pathname.startsWith(`${page.to}/`))) {
        return { section, page };
      }

      for (const subPage of page.children ?? []) {
        if (subPage.to && (pathname === subPage.to || pathname.startsWith(`${subPage.to}/`))) {
          return { section, page, subPage };
        }
      }
    }
  }

  return undefined;
}
