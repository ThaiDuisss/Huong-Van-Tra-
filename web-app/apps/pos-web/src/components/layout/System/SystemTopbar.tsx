import {
    Bell,
    CircleHelp,
    MapPin,
    Menu,
    ShieldCheck
} from "lucide-react";
import { useLocation } from "react-router-dom";

import { routePaths } from "../../../routes/path";

export function SystemTopbar() {
    const location = useLocation();
    const isOrders = location.pathname.startsWith(routePaths.orders);
    const isInvoices = location.pathname.startsWith(routePaths.invoices);
    const isInvoiceDetail =
        isInvoices && location.pathname !== routePaths.invoices;
    const section = isOrders ? "Đơn hàng" : "Khách hàng";
    const page = isInvoiceDetail
        ? "Chi tiết hóa đơn"
        : isInvoices
            ? "Hóa đơn"
            : isOrders
                ? "Tất cả đơn hàng"
                : "Danh sách khách hàng";

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

            <div className="ml-auto flex items-center gap-2">pó                <button
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
