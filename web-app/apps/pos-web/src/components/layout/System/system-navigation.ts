import {
    BarChart3,
    Boxes,
    FileText,
    FolderTree,
    History,
    Home,
    Layers3,
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
                label: "Danh sách BOM",
                to: routePaths.bomList,
                icon: Layers3
            },
            {
                label: "Đơn hàng sản xuất",
                to: routePaths.productionOrders,
                icon: Layers3
            }

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
            // {
            //     label: "Quản lý kệ hàng",
            //     to: routePaths.inventoryLocations,
            //     icon: MapPinned
            // },
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
