import {
    Bell,
    CircleHelp,
    MapPin,
    Menu,
    ShieldCheck
} from "lucide-react";
import { useMemo } from "react";
import { Link, useLocation } from "react-router-dom";

import { useAuth } from "../../../auth/auth-context";
import {
    filterNavigationByRoles,
    findNavigationTrail,
    getFirstNavigationPath,
    systemNavigationItems
} from "./system-navigation";

interface BreadcrumbResult {
    section: string;
    sectionTo?: string;
    page: string;
    pageTo?: string;
    subPage?: string;
}

const subRouteLabels: Record<string, { parentTo: string; label: string }> = {
    "/system/products/import": { parentTo: "/system/products", label: "Nhập excel" },
    "/system/products/": { parentTo: "/system/products", label: "Chi tiết sản phẩm" },
    "/system/product-variants/import": { parentTo: "/system/product-variants", label: "Nhập excel" },
    "/system/product-variants/": { parentTo: "/system/product-variants", label: "Chi tiết mặt hàng" },
    "/system/manufacturing/ingredients/import": { parentTo: "/system/manufacturing/ingredients", label: "Nhập excel" },
    "/system/orders/invoices/": { parentTo: "/system/orders/invoices", label: "Chi tiết hóa đơn" },
    "/system/manufacturing/boms/": { parentTo: "/system/manufacturing/boms", label: "Chi tiết công thức" },
    "/system/price-lists/": { parentTo: "/system/price-lists", label: "Chi tiết bảng giá" },
    "/system/marketing/discount-programs/": {
        parentTo: "/system/marketing/discount-programs",
        label: "Chi tiết chương trình giảm giá"
    },
    "/management/inventory/documents/": {
        parentTo: "/management/inventory/documents",
        label: "Chi tiết chứng từ kho"
    },
    "/management/inventory/receipts/new": {
        parentTo: "/management/inventory/receipts",
        label: "Tạo phiếu nhập kho"
    },
    "/management/inventory/receipts/import": {
        parentTo: "/management/inventory/receipts",
        label: "Nhập excel"
    },
    "/management/inventory/receipts/": {
        parentTo: "/management/inventory/receipts",
        label: "Chi tiết phiếu nhập kho"
    },
    "/management/inventory/production-orders/new": {
        parentTo: "/management/inventory/production-orders",
        label: "Tạo lệnh sản xuất"
    },
    "/management/inventory/production-orders/": {
        parentTo: "/management/inventory/production-orders",
        label: "Chi tiết lệnh sản xuất"
    },
    "/management/inventory/custom-bundles/": {
        parentTo: "/management/inventory/custom-bundles",
        label: "Chi tiết Custom Tea Bundle"
    },
    "/management/inventory/transfers/new": {
        parentTo: "/management/inventory/transfers",
        label: "Tạo phiếu chuyển kho"
    },
    "/management/inventory/transfers/": {
        parentTo: "/management/inventory/transfers",
        label: "Chi tiết chuyển kho"
    },
    "/management/inventory/stocktakes/new": {
        parentTo: "/management/inventory/stocktakes",
        label: "Tạo kỳ kiểm kê"
    },
    "/management/inventory/stocktakes/": {
        parentTo: "/management/inventory/stocktakes",
        label: "Chi tiết kiểm kê"
    },
    "/management/inventory/customer-returns/": {
        parentTo: "/management/inventory/customer-returns",
        label: "Chi tiết khách trả hàng"
    },
    "/management/inventory/supplier-returns/new": {
        parentTo: "/management/inventory/supplier-returns",
        label: "Tạo phiếu trả nhà cung cấp"
    },
    "/management/inventory/supplier-returns/": {
        parentTo: "/management/inventory/supplier-returns",
        label: "Chi tiết trả nhà cung cấp"
    },
    "/management/inventory/quality/batches/": {
        parentTo: "/management/inventory/quality",
        label: "Chi tiết lô chất lượng"
    },
    "/management/inventory/disposals/": {
        parentTo: "/management/inventory/disposals",
        label: "Chi tiết hỏng / tiêu hủy"
    },
    "/management/inventory/alerts/": {
        parentTo: "/management/inventory/alerts",
        label: "Chi tiết cảnh báo tồn kho"
    }
};

function resolveSubRouteLabel(pathname: string) {
    // Sắp xếp các prefix có độ dài lớn hơn trước để match chính xác nhất (ví dụ /system/product-variants/import trước /system/product-variants/)
    const sortedEntries = Object.entries(subRouteLabels).sort(
        (a, b) => b[0].length - a[0].length
    );

    for (const [prefix, config] of sortedEntries) {
        if (
            (pathname === prefix || pathname.startsWith(prefix.endsWith("/") ? prefix : `${prefix}/`)) &&
            pathname !== config.parentTo
        ) {
            return config.label;
        }
    }

    return undefined;
}

function resolveBreadcrumb(pathname: string, roles: readonly string[] | undefined): BreadcrumbResult {
    const visibleNavigationItems = filterNavigationByRoles(systemNavigationItems, roles);
    const trail = findNavigationTrail(visibleNavigationItems, pathname);

    if (!trail) {
        return { section: "Hệ thống", page: "Tổng quan" };
    }

    const leaf = trail.subPage ?? trail.page ?? trail.section;
    const subPage = leaf.to && pathname !== leaf.to ? resolveSubRouteLabel(pathname) : undefined;

    return {
        section: trail.section.label,
        sectionTo: getFirstNavigationPath(trail.section),
        page: trail.subPage?.label ?? trail.page?.label ?? trail.section.label,
        pageTo: trail.subPage?.to ?? trail.page?.to ?? trail.section.to,
        subPage
    };
}

export function SystemTopbar() {
    const location = useLocation();
    const { roles } = useAuth();
    const { section, sectionTo, page, pageTo, subPage } = useMemo(
        () => resolveBreadcrumb(location.pathname, roles),
        [location.pathname, roles]
    );

    return (
        <header className="flex h-[58px] shrink-0 items-center gap-4 border-b border-[#e8eef0] bg-white px-4 text-[#172317] sm:px-5">
            <button
                type="button"
                className="grid size-9 place-items-center rounded-lg text-[#53678f] hover:bg-[#f4faf5]"
                aria-label="Mở thu gọn menu"
            >
                <Menu size={20} aria-hidden="true" />
            </button>

            <nav className="flex min-w-0 items-center gap-2 text-[12px] font-semibold">
                {sectionTo ? (
                    <Link to={sectionTo} className="text-[#5f6b7a] transition-colors hover:text-[#159734] hover:underline">
                        {section}
                    </Link>
                ) : (
                    <span className="text-[#5f6b7a]">{section}</span>
                )}
                <span className="text-[#9aa7b5]">›</span>
                {subPage && pageTo ? (
                    <Link to={pageTo} className="text-[#5f6b7a] transition-colors hover:text-[#159734] hover:underline">
                        {page}
                    </Link>
                ) : pageTo ? (
                    <Link to={pageTo} className="truncate text-[#159734] hover:underline">
                        {page}
                    </Link>
                ) : (
                    <span className={subPage ? "text-[#5f6b7a]" : "truncate text-[#159734]"}>
                        {page}
                    </span>
                )}
                {subPage ? (
                    <>
                        <span className="text-[#9aa7b5]">›</span>
                        <span className="truncate text-[#159734]">
                            {subPage}
                        </span>
                    </>
                ) : null}
            </nav>

            <div className="ml-auto flex items-center gap-2">
                <button
                    type="button"
                    className="relative grid size-9 place-items-center rounded-lg border border-[#e8eef0] bg-white text-[#53678f]"
                    aria-label="Thông báo"
                >
                    <Bell size={17} aria-hidden="true" />
                    <span className="absolute right-1.5 top-1.5 size-3 rounded-full border-2 border-white bg-red-500" />
                </button>
                <button
                    type="button"
                    className="grid size-9 place-items-center rounded-lg border border-[#e8eef0] bg-white text-[#53678f]"
                    aria-label="Trợ giúp"
                >
                    <CircleHelp size={17} aria-hidden="true" />
                </button>
                <button
                    type="button"
                    className="grid size-9 place-items-center rounded-lg border border-[#d8eadc] bg-[#f7fff9] text-[#159734]"
                    aria-label="Cửa hàng đang hoạt động"
                >
                    <MapPin size={17} aria-hidden="true" />
                </button>
                <button
                    type="button"
                    className="hidden h-9 items-center gap-2 rounded-lg border border-[#e8eef0] bg-white px-3 text-[12px] font-bold xl:flex"
                >
                    <ShieldCheck
                        className="text-[#159734]"
                        size={15}
                        aria-hidden="true"
                    />
                    Cửa hàng trung tâm
                </button>
            </div>
        </header>
    );
}
