import type {
    PromotionApplyMethodValue,
    PromotionTypeValue
} from "../types/promotion.types";
import { formatFallbackEnum } from "./event-labels";

const promotionTypeLabels: Record<string, string> = {
    PERCENT_DISCOUNT: "Giảm theo phần trăm",
    FIXED_DISCOUNT: "Giảm số tiền cố định",
    FREE_SHIPPING: "Miễn phí vận chuyển",
    BUY_X_GET_Y: "Mua X tặng Y",
    FREE_PRODUCT: "Tặng sản phẩm"
};

const promotionApplyMethodLabels: Record<string, string> = {
    AUTO: "Tự động",
    COUPON_CODE: "Mã giảm giá",
    DIRECT_DISCOUNT: "Giảm trực tiếp"
};

const promotionStatusLabels: Record<string, string> = {
    DRAFT: "Bản nháp",
    SCHEDULED: "Đã lên lịch",
    ACTIVE: "Đang chạy",
    PAUSED: "Tạm dừng",
    EXPIRED: "Hết hạn",
    ENDED: "Đã kết thúc",
    CANCELLED: "Đã hủy"
};

export const promotionTypeOptions = Object.keys(
    promotionTypeLabels
) as PromotionTypeValue[];
export const promotionApplyMethodOptions = Object.keys(
    promotionApplyMethodLabels
) as PromotionApplyMethodValue[];

export function getPromotionTypeLabel(value?: string) {
    if (!value) return "-";

    return promotionTypeLabels[value] ?? formatFallbackEnum(value);
}

export function getPromotionApplyMethodLabel(value?: string) {
    if (!value) return "-";

    return promotionApplyMethodLabels[value] ?? formatFallbackEnum(value);
}

export function getPromotionStatusLabel(value?: string) {
    if (!value) return "-";

    return promotionStatusLabels[value] ?? formatFallbackEnum(value);
}
