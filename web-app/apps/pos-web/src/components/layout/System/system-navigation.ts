import {
    BarChart3,
    BadgePercent,
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
    Plane,
    RefreshCw,
    Ruler,
    Settings,
    ShoppingCart,
    Trash2,
    Truck,
    User,
    WalletCards,
    Warehouse
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import {
    BACKOFFICE_ROLES,
    INVENTORY_OPERATION_ROLES,
    INVENTORY_ROLES,
    MANUFACTURING_ROLES,
    MARKETING_ROLES,
    ORDER_ROLES,
    PRODUCT_ROLES,
    REPORT_ROLES,
    SETTINGS_ROLES,
    type AppRole,
    hasAnyRole
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
        roles: BACKOFFICE_ROLES
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
                roles: ORDER_ROLES
            },
            {
                key: "orders-invoices",
                label: "Hóa đơn",
                to: routePaths.invoices,
                icon: FileText,
                roles: ORDER_ROLES
            },
            {
                key: "orders-returns",
                label: "Hoàn hàng",
                to: routePaths.returns,
                icon: History,
                roles: ["ADMIN", "BRANCH_MANAGER"]
            },
            {
                key: "orders-refunds",
                label: "Hoàn tiền",
                to: routePaths.refunds,
                icon: WalletCards,
                roles: ["ADMIN", "BRANCH_MANAGER", "ACCOUNTANT"]
            }
        ]
    },
    {
        key: "catalog",
        label: "Hàng hóa",
        icon: Package,
        roles: PRODUCT_ROLES,
        children: [
            {
                key: "catalog-variants",
                label: "Mặt hàng",
                to: routePaths.productVariants,
                icon: Layers3,
                roles: PRODUCT_ROLES
            },
            {
                key: "catalog-products",
                label: "Sản phẩm",
                to: routePaths.products,
                icon: Package,
                roles: PRODUCT_ROLES
            },
            {
                key: "catalog-categories",
                label: "Danh mục sản phẩm",
                to: routePaths.categories,
                icon: FolderTree,
                roles: ["ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER"]
            },
            {
                key: "catalog-units",
                label: "Đơn vị tính",
                to: routePaths.units,
                icon: Ruler,
                roles: ["ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER"]
            }
        ]
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
                roles: MANUFACTURING_ROLES
            },
            {
                key: "manufacturing-custom-bundles",
                label: "Custom Tea Bundle",
                to: routePaths.customBundles,
                icon: Boxes,
                roles: MANUFACTURING_ROLES
            },
            {
                key: "manufacturing-boms",
                label: "Công thức",
                to: routePaths.bomList,
                icon: Layers3,
                roles: MANUFACTURING_ROLES
            },
            {
                key: "manufacturing-ingredients",
                label: "Nguyên liệu",
                to: routePaths.ingredient,
                icon: Layers3,
                roles: MANUFACTURING_ROLES
            }
        ]
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
                    {
                        key: "inventory-balances",
                        label: "Tồn kho hiện tại",
                        icon: Warehouse,
                        to: routePaths.inventoryBalances,
                        roles: INVENTORY_ROLES
                    },
                    {
                        key: "inventory-reorder-policies",
                        label: "Chính sách bổ sung",
                        icon: Boxes,
                        to: routePaths.inventoryReorderPolicies,
                        roles: ["ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER"]
                    },
                    {
                        key: "inventory-transactions",
                        label: "Lịch sử biến động",
                        icon: History,
                        to: routePaths.inventoryTransactions,
                        roles: ["ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER", "ACCOUNTANT"]
                    }
                ]
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
                        roles: INVENTORY_OPERATION_ROLES
                    },
                    {
                        key: "inventory-documents",
                        label: "Chứng từ kho",
                        icon: FileText,
                        to: routePaths.inventoryDocuments,
                        roles: INVENTORY_ROLES
                    }
                ]
            },
            {
                key: "inventory-transfers",
                label: "Chuyển kho",
                icon: Truck,
                to: routePaths.inventoryTransfers,
                roles: INVENTORY_OPERATION_ROLES
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
                        roles: INVENTORY_OPERATION_ROLES
                    },
                    {
                        key: "inventory-adjustments",
                        label: "Điều chỉnh kho",
                        icon: ClipboardList,
                        to: routePaths.inventoryQuality,
                        roles: ["ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER"]
                    }
                ]
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
                        roles: ["ADMIN", "BRANCH_MANAGER", "WAREHOUSE_STAFF"]
                    },
                    {
                        key: "inventory-supplier-returns",
                        label: "Trả nhà cung cấp",
                        icon: RefreshCw,
                        to: routePaths.inventorySupplierReturnList,
                        roles: ["ADMIN", "BRANCH_MANAGER", "WAREHOUSE_STAFF", "INVENTORY_MANAGER"]
                    },
                    {
                        key: "inventory-disposals",
                        label: "Hỏng / tiêu hủy",
                        icon: Trash2,
                        to: routePaths.inventoryDisposals,
                        roles: INVENTORY_OPERATION_ROLES
                    },
                    {
                        key: "inventory-alerts",
                        label: "Cảnh báo tồn kho",
                        icon: ClipboardList,
                        to: routePaths.inventoryAlerts,
                        roles: ["ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER"]
                    }
                ]
            },
            {
                key: "inventory-shelves",
                label: "Quản lý kệ",
                icon: MapPinned,
                to: routePaths.inventoryShelves,
                roles: ["ADMIN", "BRANCH_MANAGER", "INVENTORY_MANAGER"]
            }
        ]
    },
    {
        key: "customers",
        label: "Khách hàng",
        icon: User,
        roles: ["ADMIN", "BRANCH_MANAGER"],
        children: [
            {
                key: "customers-list",
                label: "Danh sách khách hàng",
                to: routePaths.customers,
                icon: User,
                roles: ["ADMIN", "BRANCH_MANAGER"]
            },
            {
                key: "customers-tiers",
                label: "Hạng khách hàng",
                to: routePaths.customerTiers,
                icon: BadgePercent,
                roles: ["ADMIN", "BRANCH_MANAGER"]
            }
        ]
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
                roles: MARKETING_ROLES
            },
            {
                key: "marketing-programs",
                label: "Chương trình giảm giá",
                to: routePaths.discountPrograms,
                icon: BadgePercent,
                roles: MARKETING_ROLES
            },
            {
                key: "marketing-codes",
                label: "Mã giảm giá",
                to: routePaths.discountCodes,
                icon: BadgePercent,
                roles: MARKETING_ROLES
            },
            {
                key: "marketing-history",
                label: "Lịch sử giảm giá",
                to: routePaths.discountHistory,
                icon: History,
                roles: MARKETING_ROLES
            }
        ]
    },
    {
        key: "reports",
        label: "Báo cáo",
        icon: BarChart3,
        roles: REPORT_ROLES
    },
    {
        key: "settings",
        label: "Cài đặt",
        icon: Settings,
        roles: SETTINGS_ROLES
    }
];

export function filterNavigationByRoles(
    items: readonly SystemNavigationItem[],
    userRoles: readonly string[] | undefined
): SystemNavigationItem[] {
    return items
        .filter((item) => hasAnyRole(userRoles, item.roles))
        .map((item) => {
            const children = item.children
                ? filterNavigationByRoles(item.children, userRoles)
                : undefined;

            return {
                ...item,
                children
            };
        })
        .filter((item) => !item.children || item.children.length > 0);
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

export function isNavigationItemActive(
    item: SystemNavigationItem,
    pathname: string
): boolean {
    if (item.to && (pathname === item.to || pathname.startsWith(`${item.to}/`))) {
        return true;
    }

    return item.children?.some((child) => isNavigationItemActive(child, pathname)) ?? false;
}

export function findNavigationTrail(
    items: readonly SystemNavigationItem[],
    pathname: string
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
                if (
                    subPage.to &&
                    (pathname === subPage.to || pathname.startsWith(`${subPage.to}/`))
                ) {
                    return { section, page, subPage };
                }
            }
        }
    }

    return undefined;
}
