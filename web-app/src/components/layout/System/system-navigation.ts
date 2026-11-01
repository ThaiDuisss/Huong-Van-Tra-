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
  Upload,
  User,
  UserCog,
  WalletCards,
  Warehouse,
  ShieldCheck,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import {
  ACCOUNT_ROLES,
  ACTION_LOG_ROLES,
  AI_REPORT_ROLES,
  BACKOFFICE_ROLES,
  CUSTOMER_ROLES,
  INVENTORY_OPERATION_ROLES,
  INVENTORY_ROLES,
  MANUFACTURING_ROLES,
  MARKETING_ROLES,
  ROLES,
  ORDER_ROLES,
  PRICE_LIST_ROLES,
  PRODUCT_ROLES,
  REPORT_ROLES,
  SELLABLE_CONFIG_ROLES,
  SETTINGS_ROLES,
  STATISTIC_REPORT_ROLES,
  VOUCHER_ROLES,
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
  /**
   * Permission (bất kỳ cái nào) để hiện mục này. Khi người dùng đã có danh sách permission thì permission
   * quyết định; chưa có (backend cũ) thì chỉ xét `roles`.
   */
  permissions?: string[];
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
        roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER],
      },
      {
        key: "orders-refunds",
        label: "Hoàn tiền",
        to: routePaths.refunds,
        icon: WalletCards,
        roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER],
      },
    ],
  },
  {
    key: "catalog",
    label: "Hàng hóa",
    icon: Package,
    roles: [...PRODUCT_ROLES, ...PRICE_LIST_ROLES],
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
        roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER],
      },
      {
        key: "catalog-units",
        label: "Đơn vị tính",
        to: routePaths.units,
        icon: Ruler,
        roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER],
      },
      {
        key: "catalog-price-lists",
        label: "Bảng giá",
        to: routePaths.priceLists,
        icon: WalletCards,
        roles: PRICE_LIST_ROLES,
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
        label: "Đóng trà theo yêu cầu",
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
            roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER],
          },
          {
            key: "inventory-transactions",
            label: "Lịch sử biến động",
            icon: History,
            to: routePaths.inventoryTransactions,
            roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER],
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
        ],
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
            roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER],
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
            roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER, ROLES.INVENTORY_STAFF],
          },
          {
            key: "inventory-disposals",
            label: "Hỏng / tiêu hủy",
            icon: Trash2,
            to: routePaths.inventoryDisposals,
            roles: INVENTORY_OPERATION_ROLES,
          },
        ],
      },
      {
        key: "inventory-shelves",
        label: "Quản lý kệ",
        icon: MapPinned,
        to: routePaths.inventoryShelves,
        roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER],
      },
    ],
  },
  {
    key: "customers",
    label: "Khách hàng",
    icon: User,
    roles: CUSTOMER_ROLES,
    children: [
      {
        key: "customers-list",
        label: "Danh sách khách hàng",
        to: routePaths.customers,
        icon: User,
        roles: CUSTOMER_ROLES,
      },
      {
        key: "customers-tiers",
        label: "Hạng khách hàng",
        to: routePaths.customerTiers,
        icon: BadgePercent,
        roles: [ROLES.SUPER_ADMIN, ROLES.MANAGER],
      },
    ],
  },
  {
    key: "marketing",
    label: "Marketing",
    icon: Plane,
    roles: VOUCHER_ROLES,
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
        roles: VOUCHER_ROLES,
      },
      {
        key: "marketing-history",
        label: "Lịch sử giảm giá",
        to: routePaths.discountHistory,
        icon: History,
        roles: VOUCHER_ROLES,
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
      {
        key: "ingredient-demo",
        label: "Gợi ý nguyên liệu (Demo)",
        to: routePaths.ingredientDemo,
        icon: Sparkles,
        roles: AI_REPORT_ROLES,
      },
    ],
  },
  {
    key: "reports",
    label: "Báo cáo",
    icon: BarChart3,
    roles: [...REPORT_ROLES, ...STATISTIC_REPORT_ROLES],
    children: [
      {
        key: "report-top-sales-staff",
        label: "Nhân viên bán hàng tốt nhất",
        to: routePaths.reportTopSalesStaff,
        icon: User,
        roles: STATISTIC_REPORT_ROLES,
      },
      {
        key: "report-top-selling-products",
        label: "Sản phẩm bán chạy",
        to: routePaths.reportTopSellingProducts,
        icon: Package,
        roles: STATISTIC_REPORT_ROLES,
      },
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
    key: "accounts",
    label: "Tài khoản",
    icon: UserCog,
    to: routePaths.systemAccounts,
    roles: ACCOUNT_ROLES,
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
    // Cùng permission với mục con và route Phân quyền, để nhóm không chặn người được cấp quyền.
    permissions: ["permission:manage", "role:manage", "user:manage"],
    children: [
      {
        key: "authorization",
        label: "Phân quyền",
        icon: ShieldCheck,
        to: routePaths.systemAuthorization,
        roles: SETTINGS_ROLES,
        permissions: ["permission:manage", "role:manage", "user:manage"],
      },
    ],
  },
];

function isItemVisible(
  item: SystemNavigationItem,
  userRoles: readonly string[] | undefined,
  userPermissions: readonly string[] | undefined,
): boolean {
  // SUPER_ADMIN luôn thấy; danh sách permission rỗng = chưa bootstrap → xét role như cũ.
  if (hasAnyRole(userRoles, [ROLES.SUPER_ADMIN])) {
    return true;
  }
  if (item.permissions && item.permissions.length > 0 && Array.isArray(userPermissions) && userPermissions.length > 0) {
    return item.permissions.some((permission) => userPermissions.includes(permission));
  }
  return hasAnyRole(userRoles, item.roles);
}

export function filterNavigationByRoles(
  items: readonly SystemNavigationItem[],
  userRoles: readonly string[] | undefined,
  userPermissions?: readonly string[],
): SystemNavigationItem[] {
  return items
    .filter((item) => isItemVisible(item, userRoles, userPermissions))
    .map((item) => {
      const children = item.children
        ? filterNavigationByRoles(item.children, userRoles, userPermissions)
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
  userPermissions?: readonly string[],
): SystemNavigationItem[] {
  const items = hasAnyRole(userRoles, BACKOFFICE_ROLES)
    ? systemNavigationItems
    : systemNavigationItems.filter((item) => item.key === "ai");

  return filterNavigationByRoles(items, userRoles, userPermissions);
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

function matchesPath(item: SystemNavigationItem, pathname: string) {
  return Boolean(item.to && (pathname === item.to || pathname.startsWith(`${item.to}/`)));
}

/**
 * Chọn mục có đường dẫn khớp dài nhất, để /system/orders/invoices thuộc "Hóa đơn"
 * chứ không thuộc "Tất cả đơn" (/system/orders) chỉ vì đứng trước trong menu.
 */
export function findNavigationTrail(
  items: readonly SystemNavigationItem[],
  pathname: string,
): NavigationTrail | undefined {
  let best: NavigationTrail | undefined;
  let bestLength = -1;

  const consider = (item: SystemNavigationItem, trail: NavigationTrail) => {
    if (matchesPath(item, pathname) && item.to!.length > bestLength) {
      best = trail;
      bestLength = item.to!.length;
    }
  };

  for (const section of items) {
    consider(section, { section });

    for (const page of section.children ?? []) {
      consider(page, { section, page });

      for (const subPage of page.children ?? []) {
        consider(subPage, { section, page, subPage });
      }
    }
  }

  return best;
}
