import {
    BarChart3,
    BadgePercent,
    Boxes,
    CalendarDays,
    FileText,
    FolderTree,
    History,
    Home,
    Layers3,
    MapPinned,
    Package,
    Plane,
    Ruler,
    Settings,
    ShoppingCart,
    User,
    Users,
    WalletCards,
    Warehouse
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { routePaths } from "../../../../routes/path";

export interface SystemNavigationChild {
    label: string;
    to: string;
    icon: LucideIcon;
}

export interface SystemNavigationItem {
    label: string;
    icon: LucideIcon;
    badge?: string;
    children?: SystemNavigationChild[];
}

export const systemNavigationItems: SystemNavigationItem[] = [
    {
        label: "Tổng quan",
        icon: Home
    },
    {
        label: "Đơn hàng",
        icon: ShoppingCart,
        children: [
            {
                label: "Tất cả đơn hàng",
                to: routePaths.orders,
                icon: FileText
            },
            {
                label: "Hóa đơn",
                to: routePaths.invoices,
                icon: FileText
            },
            {
                label: "Hoàn hàng",
                to: routePaths.returns,
                icon: History
            },
            {
                label: "Hoàn tiền",
                to: routePaths.refunds,
                icon: WalletCards
            }
        ]
    },
    {
        label: "Hàng hóa",
        icon: Package,
        children: [
            {
                label: "Mặt hàng",
                to: routePaths.productVariants,
                icon: Layers3
            },
            {
                label: "Sản phẩm",
                to: routePaths.products,
                icon: Package
            },
            {
                label: "Danh mục sản phẩm",
                to: routePaths.categories,
                icon: FolderTree
            },
            {
                label: "Đơn vị tính",
                to: routePaths.units,
                icon: Ruler
            },
            // {
            //     label: "Bảng giá",
            //     to: routePaths.priceLists,
            //     icon: Tags
            // }
        ]
    },
    {
        label: "Sản xuất",
        icon: Package,
        children: [
            {
                label: "Nguyên liệu",
                to: routePaths.ingredient,
                icon: Layers3
            },
            {
                label: "Danh sách công thức",
                to: routePaths.bomList,
                icon: Layers3
            },
            // {
            //     label: "Danh sách công thứcxuất",
            //     to: routePaths.productionOrders,
            //     icon: Layers3
            // }

        ]
    },
    {
        label: "Kho",
        icon: Boxes,
        children: [
            {
                label: "Tồn kho hiện tại",
                icon: Warehouse,
                to: routePaths.inventoryBalances
            },
            {
                label: "Quản lý kệ",
                to: routePaths.inventoryShelves,
                icon: MapPinned
            },
            {
                label: "Lịch sử biến động",
                to: routePaths.inventoryTransactions,
                icon: History
            }
        ]
    },

    {
        label: "Khách hàng",
        icon: User,
        children: [
            {
                label: "Danh sách khách hàng",
                to: routePaths.customers,
                icon: User
            },
            {
                label: "Nhóm khách hàng",
                to: routePaths.customerGroups,
                icon: Users
            }
        ]
    },
    {
        label: "Marketing",
        icon: Plane,
        children: [
            {
                label: "Sự kiện",
                to: routePaths.marketingEvents,
                icon: CalendarDays
            },
            {
                label: "Chương trình giảm giá",
                to: routePaths.discountPrograms,
                icon: BadgePercent
            },
            {
                label: "Mã giảm giá",
                to: routePaths.discountCodes,
                icon: BadgePercent
            },
            {
                label: "Lịch sử giảm giá",
                to: routePaths.discountHistory,
                icon: History
            },
            {
                label: "Các quy tắc giảm giá",
                to: routePaths.discountRules,
                icon: FileText
            }
        ]
    },
    {
        label: "Báo cáo",
        icon: BarChart3
    },
    {
        label: "Cài đặt",
        icon: Settings
    }
];
