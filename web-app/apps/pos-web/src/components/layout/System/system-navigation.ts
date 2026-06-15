import {
    BarChart3,
    Building2,
    Calculator,
    ClipboardList,
    FileText,
    FolderTree,
    Layers3,
    Package,
    Tags,
    Settings,
    ShoppingCart,
    Truck,
    User,
    Users
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
                label: "Categories",
                to: routePaths.categories,
                icon: FolderTree
            },
            {
                label: "Products",
                to: routePaths.products,
                icon: Package
            },
            {
                label: "Product Variants",
                to: routePaths.productVariants,
                icon: Layers3
            },
            {
                label: "Price Lists",
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
