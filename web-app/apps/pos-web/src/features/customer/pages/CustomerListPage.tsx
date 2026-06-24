import {
    ChevronDown,
    Download,
    Filter,
    MoreHorizontal,
    Plus,
    ScanLine,
    Search,
    X
} from "lucide-react";
import type { ReactNode } from "react";

type CustomerTier = "Vàng" | "Bạc" | "Đồng";

interface CustomerRow {
    id: string;
    code: string;
    name: string;
    phone: string;
    tier: CustomerTier;
    totalSpent: string;
    lastPurchase: string;
    status: "Hoạt động";
}

const quickFilters = [
    { label: "Tất cả", count: "12.458", active: true },
    { label: "Mới tạo", count: "126" },
    { label: "Mua gần đây", count: "1.204" },
    { label: "Sinh nhật tháng này", count: "46" }
];

const customers: CustomerRow[] = [
    {
        id: "1",
        code: "KH00124",
        name: "Nguyễn Văn An",
        phone: "0987 123 456",
        tier: "Vàng",
        totalSpent: "4.250.000đ",
        lastPurchase: "Hôm nay",
        status: "Hoạt động"
    },
    {
        id: "2",
        code: "KH00125",
        name: "Trần Thị Mai",
        phone: "0912 456 789",
        tier: "Bạc",
        totalSpent: "1.850.000đ",
        lastPurchase: "2 ngày trước",
        status: "Hoạt động"
    },
    {
        id: "3",
        code: "KH00126",
        name: "Lê Minh Hoàng",
        phone: "0904 111 222",
        tier: "Đồng",
        totalSpent: "520.000đ",
        lastPurchase: "12 ngày trước",
        status: "Hoạt động"
    },
    {
        id: "4",
        code: "KH00127",
        name: "Phạm Thu Hà",
        phone: "0988 765 432",
        tier: "Vàng",
        totalSpent: "7.400.000đ",
        lastPurchase: "18 ngày trước",
        status: "Hoạt động"
    },
    {
        id: "5",
        code: "KH00128",
        name: "Đỗ Thanh Bình",
        phone: "0936 228 190",
        tier: "Bạc",
        totalSpent: "2.310.000đ",
        lastPurchase: "22 ngày trước",
        status: "Hoạt động"
    },
    {
        id: "6",
        code: "KH00129",
        name: "Vũ Ngọc Linh",
        phone: "0971 540 228",
        tier: "Vàng",
        totalSpent: "5.920.000đ",
        lastPurchase: "25 ngày trước",
        status: "Hoạt động"
    },
    {
        id: "7",
        code: "KH00130",
        name: "Hoàng Đức Nam",
        phone: "0908 777 318",
        tier: "Đồng",
        totalSpent: "780.000đ",
        lastPurchase: "31 ngày trước",
        status: "Hoạt động"
    },
    {
        id: "8",
        code: "KH00131",
        name: "Bùi Hồng Nhung",
        phone: "0922 450 889",
        tier: "Bạc",
        totalSpent: "3.180.000đ",
        lastPurchase: "36 ngày trước",
        status: "Hoạt động"
    }
];

const tierClassNames: Record<CustomerTier, string> = {
    Vàng: "bg-[#fff2d8] text-[#a45b05]",
    Bạc: "bg-[#eef2f6] text-[#566171]",
    Đồng: "bg-[#f6eadf] text-[#8a4f22]"
};

const buttonBase =
    "inline-flex h-10 items-center justify-center gap-2 whitespace-nowrap rounded-lg px-3.5 text-[13px] font-extrabold";
const outlineButton =
    "border border-[#bfd5c6] bg-white text-[#159734] hover:bg-[#f7fff9]";
const compactButton =
    "border border-[#d9e3df] bg-white text-[#273a2b] hover:bg-[#f7fbf8]";

export function CustomerListPage() {
    return (
        <section className="h-full min-h-0 bg-[#f6f8fb]">
            <main
                className="grid h-full min-h-0 grid-rows-[auto_auto_auto_auto_auto_minmax(0,1fr)] gap-3 p-[18px] text-[#172317] max-xl:p-3.5"
                aria-labelledby="customer-title"
            >
                <header>
                    <h1
                        className="m-0 text-[28px] font-black leading-[1.15] text-[#132317]"
                        id="customer-title"
                    >
                        Khách hàng
                    </h1>
                    <p className="mt-[7px] text-[13px] font-semibold text-[#657282]">
                        Quản lý hồ sơ, lịch sử mua hàng và hạng thành viên
                    </p>
                </header>

                <div className="grid grid-cols-[minmax(360px,1fr)_auto_auto_auto_auto] items-center gap-2.5 max-xl:grid-cols-[minmax(0,1fr)_auto_auto]">
                    <label className="flex h-11 min-w-0 items-center gap-2.5 rounded-lg border border-[#dce5e1] bg-white px-[13px] text-[#7a8794] focus-within:border-[#159734] focus-within:shadow-[0_0_0_3px_rgba(21,151,52,0.12)]">
                        <Search size={18} aria-hidden="true" />
                        <input
                            className="min-w-0 flex-1 bg-transparent text-sm font-semibold text-[#172317] outline-none placeholder:text-[#8a96a3]"
                            placeholder="Tìm tên, số điện thoại hoặc mã khách hàng..."
                            type="search"
                        />
                    </label>
                    <button
                        className={`${buttonBase} border border-[#159734] bg-[#159734] text-white shadow-[0_8px_18px_rgba(21,151,52,0.2)] hover:bg-[#12862d] max-xl:col-span-full max-xl:justify-self-start`}
                        type="button"
                    >
                        <Plus size={17} aria-hidden="true" />
                        Thêm khách hàng
                    </button>
                    <button
                        className={`${buttonBase} ${compactButton}`}
                        type="button"
                    >
                        <Filter size={16} aria-hidden="true" />
                        Bộ lọc
                    </button>
                    <button
                        className={`${buttonBase} ${outlineButton} max-xl:col-span-full max-xl:justify-self-start`}
                        type="button"
                    >
                        <Download size={16} aria-hidden="true" />
                        Xuất Excel
                    </button>
                </div>

                <div className="flex flex-wrap gap-2" aria-label="Lọc nhanh">
                    {quickFilters.map((filter) => (
                        <button
                            className={`inline-flex h-8 items-center gap-1.5 rounded-lg border px-[11px] text-xs font-extrabold ${filter.active
                                ? "border-[#159734] bg-[#eaf8ed] text-[#0f7d29]"
                                : "border-[#dde6e2] bg-white text-[#5f6b7a] hover:bg-[#f7fbf8]"
                                }`}
                            key={filter.label}
                            type="button"
                        >
                            <span>{filter.label}</span>
                            <strong
                                className={
                                    filter.active
                                        ? "font-black text-[#0f7d29]"
                                        : "font-black text-[#263545]"
                                }
                            >
                                {filter.count}
                            </strong>
                        </button>
                    ))}
                </div>

                <div className="flex min-h-[30px] items-center gap-2 overflow-hidden">
                    <span className="text-xs font-extrabold text-[#697684]">
                        Bộ lọc đang áp dụng
                    </span>
                    <button
                        className="inline-flex h-7 items-center gap-1 rounded-[7px] border border-[#dce8df] bg-white px-[9px] text-xs font-extrabold text-[#334336]"
                        type="button"
                    >
                        Hạng: Vàng
                        <X size={13} aria-hidden="true" />
                    </button>
                    <button
                        className="inline-flex h-7 items-center gap-1 rounded-[7px] border border-[#dce8df] bg-white px-[9px] text-xs font-extrabold text-[#334336]"
                        type="button"
                    >
                        Mua trong 30 ngày
                        <X size={13} aria-hidden="true" />
                    </button>
                    <button
                        className="ml-auto bg-transparent text-xs font-black text-[#159734] max-xl:ml-0"
                        type="button"
                    >
                        Xóa tất cả
                    </button>
                </div>

                <div className="flex items-center justify-between gap-3.5 text-[13px] text-[#253225] max-xl:flex-col max-xl:items-start">
                    <strong className="font-black">
                        248 khách hàng phù hợp
                    </strong>
                    <button
                        className="inline-flex h-[34px] items-center justify-center gap-2 whitespace-nowrap rounded-lg border border-[#dce5e1] bg-white px-3.5 text-xs font-extrabold text-[#4c5a67] hover:bg-[#f7fbf8]"
                        type="button"
                    >
                        Sắp xếp: Lần mua gần nhất
                        <ChevronDown size={15} aria-hidden="true" />
                    </button>
                </div>

                <section className="grid min-h-0 grid-rows-[minmax(0,1fr)_auto] overflow-hidden rounded-[10px] border border-[#e1e8e5] bg-white shadow-[0_14px_34px_rgba(31,53,43,0.06)]">
                    <div className="min-h-0 overflow-auto">
                        <table className="w-full min-w-[1120px] border-separate border-spacing-0 text-[13px] text-[#233025]">
                            <thead>
                                <tr>
                                    <th className="sticky top-0 z-[1] h-[42px] w-12 border-b border-[#e8eeeb] bg-[#f8faf9] px-3.5 pr-1 text-center text-[11px] font-black uppercase text-[#556477]">
                                        <input
                                            aria-label="Chọn tất cả khách hàng"
                                            className="size-4 accent-[#159734]"
                                            type="checkbox"
                                        />
                                    </th>
                                    <TableHead>Mã KH</TableHead>
                                    <TableHead>Khách hàng</TableHead>
                                    <TableHead>Số điện thoại</TableHead>
                                    <TableHead>Hạng thành viên</TableHead>
                                    <TableHead>Tổng chi tiêu</TableHead>
                                    <TableHead>Lần mua gần nhất</TableHead>
                                    <TableHead>Trạng thái</TableHead>
                                    <TableHead>
                                        <span className="sr-only">Thao tác</span>
                                    </TableHead>
                                </tr>
                            </thead>
                            <tbody>
                                {customers.map((customer) => (
                                    <tr
                                        className="cursor-pointer group"
                                        key={customer.id}
                                    >
                                        <td className="h-[58px] w-12 whitespace-nowrap border-b border-[#eef2f0] bg-white px-3.5 pr-1 text-center align-middle group-hover:bg-[#f7fbf8]">
                                            <input
                                                aria-label={`Chọn ${customer.name}`}
                                                className="size-4 accent-[#159734]"
                                                type="checkbox"
                                            />
                                        </td>
                                        <TableCell className="font-black text-[#526071]">
                                            {customer.code}
                                        </TableCell>
                                        <TableCell>
                                            <button
                                                className="bg-transparent p-0 text-left font-black text-[#172317] hover:text-[#159734]"
                                                type="button"
                                            >
                                                {customer.name}
                                            </button>
                                        </TableCell>
                                        <TableCell>{customer.phone}</TableCell>
                                        <TableCell>
                                            <span
                                                className={`inline-flex h-[25px] items-center justify-center rounded-[7px] px-[9px] text-[11px] font-black ${tierClassNames[
                                                    customer.tier
                                                ]
                                                    }`}
                                            >
                                                {customer.tier}
                                            </span>
                                        </TableCell>
                                        <TableCell className="font-black text-[#152319]">
                                            {customer.totalSpent}
                                        </TableCell>
                                        <TableCell>
                                            {customer.lastPurchase}
                                        </TableCell>
                                        <TableCell>
                                            <span className="inline-flex h-[25px] items-center justify-center rounded-[7px] bg-[#e9f8ed] px-[9px] text-[11px] font-black text-[#159734]">
                                                {customer.status}
                                            </span>
                                        </TableCell>
                                        <TableCell>
                                            <button
                                                className="grid size-8 place-items-center rounded-lg border border-transparent bg-transparent text-[#596879] hover:border-[#dce5e1] hover:bg-white hover:text-[#172317]"
                                                type="button"
                                                aria-label={`Mở thao tác cho ${customer.name}`}
                                            >
                                                <MoreHorizontal
                                                    size={18}
                                                    aria-hidden="true"
                                                />
                                            </button>
                                        </TableCell>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    <footer className="flex min-h-[54px] items-center justify-between gap-4 border-t border-[#e8eeeb] px-3.5 py-[9px] text-xs font-extrabold text-[#657282] max-xl:flex-col max-xl:items-start">
                        <span>Đã hiển thị 100 / 248 khách hàng</span>
                        <div className="flex items-center gap-3">
                            <span>Cuộn để tải thêm</span>
                            <button
                                className={`${buttonBase} ${outlineButton}`}
                                type="button"
                            >
                                Tải thêm 100 khách hàng
                            </button>
                        </div>
                    </footer>
                </section>
            </main>
        </section>
    );
}

function TableHead({ children }: { children: ReactNode }) {
    return (
        <th className="sticky top-0 z-[1] h-[42px] whitespace-nowrap border-b border-[#e8eeeb] bg-[#f8faf9] px-3.5 text-left text-[11px] font-black uppercase text-[#556477]">
            {children}
        </th>
    );
}

function TableCell({
    children,
    className = ""
}: {
    children: ReactNode;
    className?: string;
}) {
    return (
        <td
            className={`h-[58px] whitespace-nowrap border-b border-[#eef2f0] bg-white px-3.5 align-middle group-hover:bg-[#f7fbf8] ${className}`}
        >
            {children}
        </td>
    );
}
