import {
    CalendarDays,
    CircleDollarSign,
    Clock3,
    LoaderCircle,
    Mail,
    Pencil,
    Phone,
    ShieldCheck,
    Trash2,
    UserRound,
    X
} from "lucide-react";
import { useEffect } from "react";
import { createPortal } from "react-dom";

import {
    CUSTOMER_STATUS_CLASS_NAMES,
    CUSTOMER_STATUS_LABELS
} from "../constants/customer-list.constants";
import type { CustomerResponse } from "../types/customer.types";
import { customerTierBadgeStyle } from "../utils/customer-list.utils";

interface Props {
    customer: CustomerResponse | null;
    error: string;
    loading: boolean;
    open: boolean;
    onClose: () => void;
    onDelete: (customer: CustomerResponse) => void;
    onEdit: (customer: CustomerResponse) => void;
}

export function CustomerDetailDrawer({
    customer,
    error,
    loading,
    open,
    onClose,
    onDelete,
    onEdit
}: Props) {
    useEffect(() => {
        if (!open) return;

        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        function handleKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") onClose();
        }

        window.addEventListener("keydown", handleKeyDown);
        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener("keydown", handleKeyDown);
        };
    }, [onClose, open]);

    if (!open) return null;

    function runAction(action: (customer: CustomerResponse) => void) {
        if (!customer) return;
        onClose();
        action(customer);
    }

    return createPortal(
        <div className="fixed inset-0 z-50 flex justify-end" role="presentation">
            <button
                aria-label="Đóng chi tiết khách hàng"
                className="absolute inset-0 cursor-default bg-[#101a14]/40 backdrop-blur-[1px]"
                onClick={onClose}
                type="button"
            />
            <aside
                aria-label="Chi tiết khách hàng"
                aria-modal="true"
                className="relative flex h-full w-[min(460px,100vw)] flex-col overflow-y-auto border-l border-[#dfe8e3] bg-[#f8faf9] shadow-[-18px_0_50px_rgba(21,43,30,0.16)]"
                role="dialog"
            >
                <header className="sticky top-0 z-10 flex min-h-16 items-center justify-between border-b border-[#e2e9e5] bg-white/95 px-5 backdrop-blur">
                    <div>
                        <h2 className="text-base font-black text-[#172317]">
                            Chi tiết khách hàng
                        </h2>
                        <p className="mt-0.5 text-xs font-semibold text-[#718078]">
                            Thông tin hồ sơ và mua hàng
                        </p>
                    </div>
                    <button
                        aria-label="Đóng"
                        className="grid size-9 place-items-center rounded-lg text-[#657282] hover:bg-[#eef3f0] hover:text-[#172317]"
                        onClick={onClose}
                        type="button"
                    >
                        <X size={19} aria-hidden="true" />
                    </button>
                </header>

                {loading && (
                    <div className="grid flex-1 place-items-center p-8 text-sm font-bold text-[#657282]">
                        <span className="flex items-center gap-2">
                            <LoaderCircle
                                className="animate-spin text-[#159734]"
                                size={20}
                                aria-hidden="true"
                            />
                            Đang tải chi tiết khách hàng...
                        </span>
                    </div>
                )}

                {!loading && error && (
                    <div className="m-5 rounded-lg border border-[#f1c8c1] bg-[#fff5f3] p-4 text-sm font-bold text-[#a43a2a]">
                        {error}
                    </div>
                )}

                {!loading && !error && customer && (
                    <div className="flex flex-col gap-4 p-5">
                        <section className="rounded-xl border border-[#e0e8e4] bg-white p-5 shadow-[0_8px_24px_rgba(31,53,43,0.05)]">
                            <div className="flex items-start gap-3.5">
                                <div className="grid size-12 shrink-0 place-items-center rounded-full bg-[#e8f7ec] text-lg font-black text-[#12832c]">
                                    {customer.fullName.trim().charAt(0).toUpperCase()}
                                </div>
                                <div className="min-w-0 flex-1">
                                    <h3 className="truncate text-lg font-black text-[#172317]">
                                        {customer.fullName}
                                    </h3>
                                    <p className="mt-1 text-xs font-bold text-[#718078]">
                                        {customer.customerCode}
                                    </p>
                                </div>
                                <span
                                    className="inline-flex h-7 shrink-0 items-center rounded-lg px-2.5 text-[11px] font-black"
                                    style={customerTierBadgeStyle(customer.tierColor)}
                                >
                                    {customer.tierName || "Chưa xếp hạng"}
                                </span>
                            </div>

                            <div className="mt-5 grid grid-cols-2 gap-3">
                                <StatCard
                                    icon={CircleDollarSign}
                                    label="Tổng chi tiêu"
                                    value={formatCurrency(customer.totalSpent)}
                                />
                                <StatCard
                                    icon={Clock3}
                                    label="Lần mua gần nhất"
                                    value={formatDate(customer.lastPurchaseDate)}
                                />
                            </div>
                        </section>

                        <section className="rounded-xl border border-[#e0e8e4] bg-white p-5">
                            <h3 className="mb-4 text-sm font-black text-[#172317]">
                                Thông tin liên hệ
                            </h3>
                            <div className="space-y-4">
                                <InfoRow icon={Phone} label="Số điện thoại" value={customer.phone} />
                                <InfoRow icon={Mail} label="Email" value={customer.email || "Chưa cập nhật"} />
                                <InfoRow
                                    icon={CalendarDays}
                                    label="Ngày sinh"
                                    value={formatDate(customer.dateOfBirth)}
                                />
                            </div>
                        </section>

                        <section className="rounded-xl border border-[#e0e8e4] bg-white p-5">
                            <h3 className="mb-4 text-sm font-black text-[#172317]">
                                Thông tin tài khoản
                            </h3>
                            <div className="space-y-4">
                                <InfoRow
                                    icon={UserRound}
                                    label="Ngày tham gia"
                                    value={formatDate(customer.createdAt)}
                                />
                                <InfoRow
                                    icon={CircleDollarSign}
                                    label="Hạn mức tín dụng"
                                    value={formatCurrency(customer.creditLimit)}
                                />
                                <InfoRow
                                    icon={ShieldCheck}
                                    label="Trạng thái"
                                    value={
                                        <span
                                            className={`inline-flex rounded-lg px-2.5 py-1 text-[11px] font-black ${CUSTOMER_STATUS_CLASS_NAMES[customer.status]}`}
                                        >
                                            {CUSTOMER_STATUS_LABELS[customer.status]}
                                        </span>
                                    }
                                />
                            </div>
                        </section>
                    </div>
                )}

                {!loading && !error && customer && (
                    <footer className="sticky bottom-0 mt-auto flex gap-3 border-t border-[#e2e9e5] bg-white/95 p-4 backdrop-blur">
                        <button
                            className="inline-flex h-10 flex-1 items-center justify-center gap-2 rounded-lg border border-[#bfd5c6] bg-white text-sm font-black text-[#159734] hover:bg-[#f3fbf5]"
                            onClick={() => runAction(onEdit)}
                            type="button"
                        >
                            <Pencil size={16} aria-hidden="true" />
                            Chỉnh sửa
                        </button>
                        <button
                            className="inline-flex h-10 flex-1 items-center justify-center gap-2 rounded-lg border border-[#efc9c2] bg-white text-sm font-black text-[#c4422e] hover:bg-[#fff5f3]"
                            onClick={() => runAction(onDelete)}
                            type="button"
                        >
                            <Trash2 size={16} aria-hidden="true" />
                            Xóa
                        </button>
                    </footer>
                )}
            </aside>
        </div>,
        document.body
    );
}

function StatCard({
    icon: Icon,
    label,
    value
}: {
    icon: typeof Phone;
    label: string;
    value: string;
}) {
    return (
        <div className="rounded-lg bg-[#f4f8f5] p-3.5">
            <span className="flex items-center gap-1.5 text-[11px] font-extrabold text-[#718078]">
                <Icon size={14} aria-hidden="true" />
                {label}
            </span>
            <strong className="mt-2 block text-sm font-black text-[#172317]">
                {value}
            </strong>
        </div>
    );
}

function InfoRow({
    icon: Icon,
    label,
    value
}: {
    icon: typeof Phone;
    label: string;
    value: React.ReactNode;
}) {
    return (
        <div className="grid grid-cols-[20px_120px_minmax(0,1fr)] items-center gap-2 text-xs">
            <Icon className="text-[#718078]" size={16} aria-hidden="true" />
            <span className="font-bold text-[#718078]">{label}</span>
            <strong className="min-w-0 break-words text-right font-black text-[#26342a]">
                {value}
            </strong>
        </div>
    );
}

function formatCurrency(value: number) {
    return new Intl.NumberFormat("vi-VN", {
        style: "currency",
        currency: "VND",
        maximumFractionDigits: 0
    }).format(value);
}

function formatDate(value: string | null) {
    if (!value) return "Chưa cập nhật";
    return new Intl.DateTimeFormat("vi-VN").format(new Date(value));
}
