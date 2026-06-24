import {
    Bell,
    CircleHelp,
    MapPin,
    Menu,
    ShieldCheck
} from "lucide-react";
import { useLocation } from "react-router-dom";


export function SystemTopbar() {
    const location = useLocation();
    const pathname = location.pathname;

    let section = "Hệ thống";
    let page = "Tổng quan";

    if (pathname.includes("/system/orders/invoices/")) {
        section = "Đơn hàng";
        page = "Chi tiết hóa đơn";
    } else if (pathname === "/system/orders/invoices") {
        section = "Đơn hàng";
        page = "Hóa đơn";
    } else if (pathname === "/system/orders/returns") {
        section = "Đơn hàng";
        page = "Hoàn hàng";
    } else if (pathname === "/system/orders/refunds") {
        section = "Đơn hàng";
        page = "Hoàn tiền";
    } else if (pathname === "/system/orders") {
        section = "Đơn hàng";
        page = "Tất cả đơn hàng";
    } else if (pathname.includes("/system/products/") && pathname.includes("/variants")) {
        section = "Sản phẩm";
        page = "Biến thể của sản phẩm";
    } else if (pathname.includes("/system/products/")) {
        section = "Sản phẩm";
        page = "Chi tiết sản phẩm";
    } else if (pathname === "/system/products") {
        section = "Sản phẩm";
        page = "Sản phẩm";
    } else if (pathname === "/system/categories") {
        section = "Sản phẩm";
        page = "Danh mục sản phẩm";
    } else if (pathname.includes("/system/product-variants/")) {
        section = "Sản phẩm";
        page = "Chi tiết biến thể";
    } else if (pathname === "/system/product-variants") {
        section = "Sản phẩm";
        page = "Biến thể sản phẩm";
    } else if (pathname === "/system/inventory/locations") {
        section = "Kho";
        page = "Quản lý kệ hàng";
    } else if (pathname === "/system/inventory/stock-in") {
        section = "Kho";
        page = "Nhập hàng lên kệ";
    } else if (pathname === "/system/inventory/adjustment") {
        section = "Kho";
        page = "Điều chỉnh tồn kho";
    } else if (pathname === "/system/inventory/transactions") {
        section = "Kho";
        page = "Lịch sử biến động";
    } else if (pathname === "/system/customers/groups") {
        section = "Khách hàng";
        page = "Nhóm khách hàng";
    } else if (pathname === "/system/customers") {
        section = "Khách hàng";
        page = "Danh sách khách hàng";
    }

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
                <span className="text-[#5f6b7a]">{section}</span>
                <span className="text-[#9aa7b5]">›</span>
                <span className="truncate text-[#159734]">
                    {page}
                </span>
            </nav>

            <div className="ml-auto flex items-center gap-2">                <button
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
