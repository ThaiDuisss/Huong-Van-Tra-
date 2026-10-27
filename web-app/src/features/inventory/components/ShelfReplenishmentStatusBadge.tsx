import type { ShelfReplenishmentStatus } from "../types/shelfReplenishment.types";
import { shelfReplenishmentStatusLabel } from "../utils/shelfReplenishment.utils";

const statusClass: Record<ShelfReplenishmentStatus, string> = {
    DRAFT: "bg-neutral-100 text-neutral-600",
    PENDING_APPROVAL: "bg-amber-50 text-amber-700",
    POSTED: "bg-[#eaf7ec] text-[#07883a]",
    CANCELLED: "bg-red-50 text-red-700"
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
        <span
            className={`inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-black ${
                waitingReview ? "bg-sky-50 text-sky-700" : (statusClass[status] ?? statusClass.DRAFT)
            }`}
        >
            {waitingReview ? "Đã xuất · chờ duyệt" : (shelfReplenishmentStatusLabel[status] ?? status)}
        </span>
    );
}
