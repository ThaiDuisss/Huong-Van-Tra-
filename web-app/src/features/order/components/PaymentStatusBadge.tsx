import { getPaymentStatusLabel } from "../utils/order-formatters";

const PAYMENT_STYLES: Record<string, string> = {
    PAID: "bg-emerald-50 text-emerald-700",
    UNPAID: "bg-orange-50 text-orange-600",
    WAITING_PAYMENT: "bg-orange-50 text-orange-600",
    PENDING: "bg-orange-50 text-orange-600",
    PARTIAL: "bg-sky-50 text-sky-700",
    FAILED: "bg-red-50 text-red-600",
    REFUNDED: "bg-violet-50 text-violet-700",
    PARTIALLY_REFUNDED: "bg-violet-50 text-violet-700"
};

interface PaymentStatusBadgeProps {
    status?: string | null;
    label?: string | null;
}

export function PaymentStatusBadge({
    status,
    label
}: PaymentStatusBadgeProps) {
    const colorClass = status ? PAYMENT_STYLES[status] : undefined;

    return (
        <span
            className={`inline-flex h-7 max-w-full items-center rounded-md px-2.5 text-[13px] font-black ${
                colorClass ?? "bg-slate-100 text-slate-600"
            }`}
        >
            <span className="truncate">
                {label?.trim() || getPaymentStatusLabel(status)}
            </span>
        </span>
    );
}
