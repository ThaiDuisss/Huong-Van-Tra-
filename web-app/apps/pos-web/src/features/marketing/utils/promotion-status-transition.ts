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
        { action: "pause", label: "Tạm khóa" },
        { action: "cancel", label: "Hủy", danger: true }
    ],
    ACTIVE: [
        { action: "pause", label: "Tạm khóa" },
        { action: "cancel", label: "Hủy", danger: true },
        { action: "expire", label: "Đánh dấu hết hạn" }
    ],
    INACTIVE: [
        { action: "resume", label: "Tiếp tục" },
        { action: "cancel", label: "Hủy", danger: true },
        { action: "expire", label: "Đánh dấu hết hạn" }
    ],
    EXPIRED: [
        { action: "duplicate", label: "Sao chép" },
        { action: "cancel", label: "Hủy", danger: true }
    ],
    CANCELLED: [{ action: "duplicate", label: "Sao chép" }]
};

export function getAllowedPromotionActions(
    promotion: PromotionResponse
) {
    return actionsByStatus[promotion.status] ?? [];
}

export function canEditPromotion(promotion: PromotionResponse) {
    return promotion.status === "DRAFT" || promotion.status === "SCHEDULED";
}

export function canDeletePromotion(promotion: PromotionResponse) {
    return promotion.status === "DRAFT" || promotion.status === "CANCELLED";
}
