import type { ReturnStatus } from "../types/order-return.types";
import {
    getReturnDisplayStatusLabel,
    getReturnStatusLabel,
    type ReturnDisplayStatus
} from "../utils/order-return.utils";

const STATUS_TONE: Record<string, string> = {
    CREATED: "bg-[#eef4ff] text-[#2563eb]",
    RECEIVED: "bg-[#e0f2fe] text-[#0284c7]",
    PROCESSING: "bg-[#fef3c7] text-[#b45309]",
    PROCESSING_FAILED: "border border-[#fecaca] bg-[#fef2f2] text-[#b91c1c]",
    PENDING_REFUND: "bg-[#f3e8ff] text-[#7e22ce]",
    COMPLETED: "bg-[#e9f8ed] text-[#159734]",
    CANCELLED: "bg-[#ffecec] text-[#df2525]"
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

    return (
        <span
            className={`inline-flex h-7 min-w-[92px] items-center justify-center whitespace-nowrap rounded-md px-2.5 text-[13px] font-black ${STATUS_TONE[effectiveStatus] ?? STATUS_TONE.CREATED}`}
        >
            {label}
        </span>
    );
}
