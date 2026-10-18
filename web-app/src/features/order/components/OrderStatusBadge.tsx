import { getOrderStatusLabel } from "../utils/order-formatters";

const STATUS_STYLES: Record<string, string> = {
    COMPLETED: "bg-emerald-50 text-emerald-700",
    PENDING: "bg-orange-50 text-orange-600",
    PENDING_PAYMENT: "bg-orange-50 text-orange-600",
    UNPAID: "bg-orange-50 text-orange-600",
    PROCESSING: "bg-sky-50 text-sky-700",
    PAYMENT_CONFIRMED: "bg-sky-50 text-sky-700",
    SHIPPING: "bg-blue-50 text-blue-700",
    CANCELLED: "bg-red-50 text-red-600",
    RETURNED: "bg-violet-50 text-violet-700",
    REFUNDED: "bg-violet-50 text-violet-700",
    PAYMENT_FAILED: "bg-red-50 text-red-600",
    EXPIRED: "bg-slate-100 text-slate-600",
    DRAFT: "bg-slate-100 text-slate-600",
    FAILED: "bg-red-50 text-red-600",
    QC_COMPLETED: "bg-teal-50 text-teal-700"
};

interface OrderStatusBadgeProps {
    status?: string | null;
    label?: string | null;
}

export function OrderStatusBadge({ status, label }: OrderStatusBadgeProps) {
    const colorClass = status ? STATUS_STYLES[status] : undefined;

    return (
        <span
            className={`inline-flex h-7 max-w-full items-center rounded-md px-2.5 text-[13px] font-black ${
                colorClass ?? "bg-slate-100 text-slate-600"
            }`}
        >
            <span className="truncate">
                {label?.trim() || getOrderStatusLabel(status)}
            </span>
        </span>
    );
}
