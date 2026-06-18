import {
    Bell,
    CircleHelp,
    MapPin,
    Menu,
    Search,
    ShieldCheck
} from "lucide-react";

export function SystemTopbar() {
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
                <span className="text-[#5f6b7a]">Khách hàng</span>
                <span className="text-[#9aa7b5]">›</span>
                <span className="truncate text-[#159734]">
                    Danh sách khách hàng
                </span>
            </nav>

            <label className="ml-auto flex h-10 w-[360px] max-w-[38vw] items-center gap-3 rounded-lg border border-[#e8eef0] bg-white px-3 shadow-[0_6px_16px_rgba(31,53,43,0.04)] focus-within:border-[#8fd6a5]">
                <Search
                    className="shrink-0 text-[#53678f]"
                    size={17}
                    aria-hidden="true"
                />
                <input
                    className="min-w-0 flex-1 bg-transparent text-[12px] outline-none placeholder:text-[#8b95a4]"
                    aria-label="Tìm kiếm khách hàng"
                    placeholder="Tìm kiếm mã khách hàng, tên, SĐT, email..."
                />
                <span className="text-[11px] font-bold text-[#53678f]">
                    ⌘ K
                </span>
            </label>

            <div className="flex items-center gap-2">
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
