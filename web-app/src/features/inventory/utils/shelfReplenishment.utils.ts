import { routePaths } from "../../../routes/path";
import type {
    ShelfReplenishmentQuery,
    ShelfReplenishmentStatus,
    ShelfReplenishmentSummary
} from "../types/shelfReplenishment.types";

export const shelfReplenishmentStatusLabel: Record<ShelfReplenishmentStatus, string> = {
    DRAFT: "Nháp",
    PENDING_APPROVAL: "Chờ duyệt",
    POSTED: "Đã lên kệ",
    REJECTED: "Bị từ chối",
    CANCELLED: "Đã hủy"
};

/** Bộ lọc trạng thái dùng chung cho trang Kho và màn POS. PENDING_REVIEW = đã xuất lên kệ nhưng chưa duyệt lại. */
export type ShelfReplenishmentStatusFilter = "ALL" | "PENDING_REVIEW" | ShelfReplenishmentStatus;

export const shelfReplenishmentStatusTabs: Array<{
    value: ShelfReplenishmentStatusFilter;
    label: string;
    count?: keyof ShelfReplenishmentSummary;
}> = [
    { value: "ALL", label: "Tất cả" },
    { value: "DRAFT", label: "Nháp", count: "draft" },
    { value: "PENDING_APPROVAL", label: "Chờ duyệt", count: "pending" },
    { value: "PENDING_REVIEW", label: "Đã xuất · chờ duyệt", count: "pendingReview" },
    { value: "POSTED", label: "Đã lên kệ", count: "posted" },
    { value: "REJECTED", label: "Bị từ chối", count: "rejected" },
    { value: "CANCELLED", label: "Đã hủy", count: "cancelled" }
];

export function statusFilterToQuery(
    filter: ShelfReplenishmentStatusFilter
): Pick<ShelfReplenishmentQuery, "status" | "pendingReview"> {
    return {
        status: filter === "ALL" || filter === "PENDING_REVIEW" ? undefined : filter,
        pendingReview: filter === "PENDING_REVIEW" ? true : undefined
    };
}

const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
});

export function formatReplenishmentDateTime(value: string | null | undefined) {
    if (!value) return "—";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "—" : dateTimeFormatter.format(date);
}

const quantityFormatter = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });

export function formatReplenishmentQuantity(value: number | null | undefined) {
    return quantityFormatter.format(Number(value ?? 0));
}

export function shelfReplenishmentDetailPath(id: string) {
    return routePaths.shelfReplenishmentDetail.replace(":slipId", encodeURIComponent(id));
}

export function shelfReplenishmentEditPath(id: string) {
    return routePaths.shelfReplenishmentEdit.replace(":slipId", encodeURIComponent(id));
}
