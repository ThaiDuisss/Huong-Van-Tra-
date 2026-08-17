import type {
    PromotionResponse,
    PromotionStatusValue
} from "../types/promotion.types";

export type PromotionActionType =
    | "schedule"
    | "activate"
    | "pause"
    | "resume"
    | "cancel"
    | "expire"
    | "end"
    | "duplicate";

interface PromotionAction {
    action: PromotionActionType;
    label: string;
    danger?: boolean;
}

const actionsByStatus: Record<PromotionStatusValue, PromotionAction[]> = {
    DRAFT: [
        { action: "schedule", label: "Lên lịch" },
        { action: "activate", label: "Kích hoạt" },
        { action: "cancel", label: "Hủy", danger: true }
    ],
    SCHEDULED: [
        { action: "activate", label: "Kích hoạt" },
        { action: "pause", label: "Tạm dừng" },
        { action: "end", label: "Kết thúc" },
        { action: "cancel", label: "Hủy", danger: true }
    ],
    ACTIVE: [
        { action: "pause", label: "Tạm dừng" },
        { action: "end", label: "Kết thúc" },
        { action: "cancel", label: "Hủy", danger: true },
        { action: "expire", label: "Đánh dấu hết hạn" }
    ],
    PAUSED: [
        { action: "resume", label: "Tiếp tục" },
        { action: "end", label: "Kết thúc" },
        { action: "cancel", label: "Hủy", danger: true },
        { action: "expire", label: "Đánh dấu hết hạn" }
    ],
    EXPIRED: [
        { action: "end", label: "Kết thúc" },
        { action: "duplicate", label: "Sao chép" }
    ],
    ENDED: [],
    CANCELLED: []
};

export function getAllowedPromotionActions(
    promotion: PromotionResponse
) {
    return actionsByStatus[promotion.status] ?? [];
}

export function canEditPromotion(promotion: PromotionResponse) {
    return (
        promotion.status === "DRAFT" ||
        promotion.status === "SCHEDULED" ||
        promotion.status === "PAUSED"
    );
}

export function canDeletePromotion(promotion: PromotionResponse) {
    return promotion.status === "DRAFT";
}
