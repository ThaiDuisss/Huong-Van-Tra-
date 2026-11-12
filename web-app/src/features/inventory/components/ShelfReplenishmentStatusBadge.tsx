import { StatusBadge, type BadgeTone } from "@/components/ui/StatusBadge";

import type { ShelfReplenishmentStatus } from "../types/shelfReplenishment.types";
import { shelfReplenishmentStatusLabel } from "../utils/shelfReplenishment.utils";

const statusTone: Record<ShelfReplenishmentStatus, BadgeTone> = {
    DRAFT: "neutral",
    PENDING_APPROVAL: "warning",
    POSTED: "success",
    REJECTED: "pending",
    CANCELLED: "danger"
};

export function ShelfReplenishmentStatusBadge({
    status,
    pendingReview = false
}: {
    status: ShelfReplenishmentStatus;
    /** Đã xuất hàng lên kệ (POS bán ngay) nhưng manager chưa duyệt. */
    pendingReview?: boolean;
}) {
    const waitingReview = status === "POSTED" && pendingReview;

    return (
        <StatusBadge tone={waitingReview ? "sky" : (statusTone[status] ?? "neutral")}>
            {waitingReview ? "Đã xuất · chờ duyệt" : (shelfReplenishmentStatusLabel[status] ?? status)}
        </StatusBadge>
    );
}
