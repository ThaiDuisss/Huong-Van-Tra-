import { StatusBadge, type BadgeTone } from "@/components/ui/StatusBadge";

import { getOrderStatusLabel } from "../utils/order-formatters";

const STATUS_TONE: Record<string, BadgeTone> = {
    COMPLETED: "success",
    PENDING: "pending",
    PENDING_PAYMENT: "pending",
    UNPAID: "pending",
    PROCESSING: "sky",
    PAYMENT_CONFIRMED: "sky",
    SHIPPING: "info",
    CANCELLED: "danger",
    RETURNED: "violet",
    REFUNDED: "violet",
    PAYMENT_FAILED: "danger",
    EXPIRED: "neutral",
    DRAFT: "neutral",
    FAILED: "danger",
    QC_COMPLETED: "teal"
};

interface OrderStatusBadgeProps {
    status?: string | null;
    label?: string | null;
}

export function OrderStatusBadge({ status, label }: OrderStatusBadgeProps) {
    return (
        <StatusBadge tone={(status && STATUS_TONE[status]) || "neutral"}>
            {label?.trim() || getOrderStatusLabel(status)}
        </StatusBadge>
    );
}
