import {
    BarChart3,
    Building2,
    Calculator,
    ClipboardList,
    FileText,
    FolderTree,
    History,
    Layers3,
    MapPinned,
    Package,
    PackagePlus,
    SlidersHorizontal,
    Tags,
    Settings,
    ShoppingCart,
    Truck,
    User,
    Users,
    Warehouse
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { routePaths } from "../../../routes/path";

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
        icon: BarChart3
    },
    {
        label: "Hàng hóa",
        icon: Package,
        children: [
            {
                label: "Danh mục sản phẩm",
                to: routePaths.categories,
                icon: FolderTree
            },
            {
                label: "Sản phẩm",
                to: routePaths.products,
                icon: Package
            },
            {
                label: "Biến thể sản phẩm",
                to: routePaths.productVariants,
                icon: Layers3
            },
            {
                label: "Bảng giá",
                to: routePaths.priceLists,
                icon: Tags
            }
        ]
    },
    {
        label: "BOM & Sản xuất",
        icon: ClipboardList,
        badge: "3",
        children: [
            {
                label: "Danh sách BOM",
                to: routePaths.bomList,
                icon: Package
            },
            {
                label: "Lệnh sản xuất",
                to: routePaths.productionOrders,
                icon: ClipboardList
            }
        ]
    },
    {
        label: "Quản lý tồn kho",
        icon: Warehouse,
        children: [
            {
                label: "Quản lý kệ hàng",
                to: routePaths.inventoryLocations,
                icon: MapPinned
            },
            {
                label: "Tồn kho hiện tại",
                to: routePaths.inventoryBalances,
                icon: Warehouse
            },
            {
                label: "Nhập hàng lên kệ",
                to: routePaths.inventoryStockIn,
                icon: PackagePlus
            },
            {
                label: "Điều chỉnh tồn kho",
                to: routePaths.inventoryAdjustment,
                icon: SlidersHorizontal
            },
            {
                label: "Lịch sử biến động",
                to: routePaths.inventoryTransactions,
                icon: History
            }
        ]
    },
    {
        label: "Mua hàng",
        icon: ShoppingCart
    },
    {
        label: "Đơn hàng",
        icon: FileText
    },
    {
        label: "Giao hàng",
        icon: Truck,
        badge: "5"
    },
    {
        label: "Khách hàng",
        icon: User
    },
    {
        label: "Nhân viên",
        icon: Users
    },
    {
        label: "Sổ quỹ",
        icon: Calculator
    },
    {
        label: "Báo cáo",
        icon: BarChart3
    },
    {
        label: "Bán online",
        icon: Building2,
        badge: "12"
    },
    {
        label: "Thuế & Kế toán",
        icon: FileText
    },
    {
        label: "Cài đặt",
        icon: Settings
    }
];
