import { routePaths } from "../../../routes/path";
import type { ShelfReplenishmentStatus } from "../types/shelfReplenishment.types";

export const shelfReplenishmentStatusLabel: Record<ShelfReplenishmentStatus, string> = {
    DRAFT: "Nháp",
    PENDING_APPROVAL: "Chờ kho xử lý",
    POSTED: "Đã lên kệ",
    CANCELLED: "Đã hủy"
};

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
