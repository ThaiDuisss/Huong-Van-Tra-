import { StatusBadge, type BadgeTone } from "@/components/ui/StatusBadge";

import type { ReturnStatus } from "../types/order-return.types";
import {
    getReturnDisplayStatusLabel,
    getReturnStatusLabel,
    type ReturnDisplayStatus
} from "../utils/order-return.utils";

const STATUS_TONE: Record<string, BadgeTone> = {
    CREATED: "info",
    RECEIVED: "sky",
    PROCESSING: "warning",
    PROCESSING_FAILED: "danger",
    PENDING_REFUND: "violet",
    COMPLETED: "success",
    CANCELLED: "danger"
};

/** Trạng thái nghiệp vụ của phiếu hoàn (ưu tiên `displayStatus` do backend tính). */
export function ReturnStatusBadge({
    status,
    displayStatus
}: {
    status?: ReturnStatus | null;
    displayStatus?: ReturnDisplayStatus | null;
}) {
    const effectiveStatus = displayStatus ?? status ?? "CREATED";
    const label = displayStatus ? getReturnDisplayStatusLabel(displayStatus) : getReturnStatusLabel(status);

    return <StatusBadge tone={STATUS_TONE[effectiveStatus] ?? "info"}>{label}</StatusBadge>;
}
