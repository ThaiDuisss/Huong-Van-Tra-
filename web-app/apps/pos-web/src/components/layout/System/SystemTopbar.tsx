import {
    Bell,
    CircleHelp,
    MapPin,
    Menu,
    ShieldCheck
} from "lucide-react";
import { useLocation, Link } from "react-router-dom";

import { systemNavigationItems } from "./system-navigation";

interface BreadcrumbResult {
    section: string;
    sectionTo?: string;
    page: string;
    pageTo?: string;
    subPage?: string;
}

/**
 * Duyệt qua systemNavigationItems để tìm section + page label
 * khớp với pathname hiện tại (so sánh prefix để hỗ trợ dynamic routes).
 */
function resolveBreadcrumb(pathname: string): BreadcrumbResult {
    // Check exact matches first
    for (const item of systemNavigationItems) {
        if (!item.children) continue;

        for (const child of item.children) {
            if (pathname === child.to) {
                const firstChild = item.children?.[0];
                return {
                    section: item.label,
                    sectionTo: firstChild?.to,
                    page: child.label,
                    pageTo: child.to
                };
            }
        }
    }

    // Check detail pages sub-routes
    const subRouteLabels: Record<string, { parentTo: string; label: string }> = {
        "/system/products/": { parentTo: "/system/products", label: "Chi tiết sản phẩm" },
        "/system/product-variants/": { parentTo: "/system/product-variants", label: "Chi tiết Mặt hàng" },
        "/system/orders/invoices/": { parentTo: "/system/orders/invoices", label: "Chi tiết hóa đơn" },
        "/system/manufacturing/boms/": { parentTo: "/system/manufacturing/boms", label: "Chi tiết công thức" },
        "/system/price-lists/": { parentTo: "/system/price-lists", label: "Chi tiết bảng giá" },
        "/system/marketing/discount-programs/": { parentTo: "/system/marketing/discount-programs", label: "Chi tiết chương trình giảm giá" },
    };

    for (const [prefix, config] of Object.entries(subRouteLabels)) {
        if (pathname.startsWith(prefix) && pathname !== config.parentTo) {
            for (const item of systemNavigationItems) {
                if (!item.children) continue;
                for (const child of item.children) {
                    if (child.to === config.parentTo) {
                        const firstChild = item.children?.[0];
                        return {
                            section: item.label,
                            sectionTo: firstChild?.to,
                            page: child.label,
                            pageTo: child.to,
                            subPage: config.label
                        };
                    }
                }
            }
        }
    }

    // Fallback: search by prefix match
    for (const item of systemNavigationItems) {
        if (!item.children) continue;

        for (const child of item.children) {
            if (pathname.startsWith(child.to + "/")) {
                const firstChild = item.children?.[0];
                return {
                    section: item.label,
                    sectionTo: firstChild?.to,
                    page: child.label,
                    pageTo: child.to
                };
            }
        }
    }

    // Fallback mặc định
    return { section: "Hệ thống", page: "Tổng quan" };
}

export function SystemTopbar() {
    const location = useLocation();
    const { section, sectionTo, page, pageTo, subPage } = resolveBreadcrumb(location.pathname);

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
                    <Link to={sectionTo} className="text-[#5f6b7a] hover:text-[#159734] transition-colors hover:underline">
                        {section}
                    </Link>
                ) : (
                    <span className="text-[#5f6b7a]">{section}</span>
                )}
                <span className="text-[#9aa7b5]">›</span>
                {subPage && pageTo ? (
                    <Link to={pageTo} className="text-[#5f6b7a] hover:text-[#159734] transition-colors hover:underline">
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
                {subPage && (
                    <>
                        <span className="text-[#9aa7b5]">›</span>
                        <span className="truncate text-[#159734]">
                            {subPage}
                        </span>
                    </>
                )}
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
