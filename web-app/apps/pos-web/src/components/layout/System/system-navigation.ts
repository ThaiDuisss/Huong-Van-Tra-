import {
    BarChart3,
    Boxes,
    FileText,
    FolderTree,
    History,
    Home,
    Layers3,
    MapPinned,
    Package,
    PackagePlus,
    Plane,
    SlidersHorizontal,
    Tags,
    Settings,
    ShoppingCart,
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
        icon: Home
    },
    {
        label: "Đơn hàng",
        icon: ShoppingCart,
        children: [
            {
                label: "Danh sách đơn hàng",
                to: routePaths.bomList,
                icon: FileText
            }
        ]
    },
    {
        label: "Sản phẩm",
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
        label: "Kho",
        icon: Boxes,
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
        icon: Plane
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
