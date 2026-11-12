import { StatusBadge, type BadgeTone } from "@/components/ui/StatusBadge";

import { getPaymentStatusLabel } from "../utils/order-formatters";

const PAYMENT_TONE: Record<string, BadgeTone> = {
    PAID: "success",
    UNPAID: "pending",
    WAITING_PAYMENT: "pending",
    PENDING: "pending",
    PARTIAL: "sky",
    FAILED: "danger",
    REFUNDED: "violet",
    PARTIALLY_REFUNDED: "violet"
};

interface PaymentStatusBadgeProps {
    status?: string | null;
    label?: string | null;
}

export function PaymentStatusBadge({ status, label }: PaymentStatusBadgeProps) {
    return (
        <StatusBadge tone={(status && PAYMENT_TONE[status]) || "neutral"}>
            {label?.trim() || getPaymentStatusLabel(status)}
        </StatusBadge>
    );
}
