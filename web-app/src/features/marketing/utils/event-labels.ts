import type { BadgeTone } from "../../../components/ui/StatusBadge";

const eventTypeLabels: Record<string, string> = {
    TIME_BASED: "Theo thời gian",
    ORDER_AMOUNT_BASED: "Theo giá trị đơn hàng",
    ORDER_COUNT_BASED: "Theo số lượng đơn",
    ITEM_QUANTITY_BASED: "Theo số lượng sản phẩm",
    LOYALTY_POINT_BASED: "Theo điểm thân thiết",
    SEASONAL_CAMPAIGN: "Chiến dịch theo mùa",
    FLASH_SALE: "Flash sale",
    CLEARANCE_CAMPAIGN: "Xả hàng tồn",
    LOYALTY_CAMPAIGN: "Khách hàng thân thiết",
    ORDER_INCENTIVE: "Kích thích đơn hàng",
    MANUAL_CAMPAIGN: "Chiến dịch thủ công"
};

const eventAudienceLabels: Record<string, string> = {
    ALL_CUSTOMERS: "Tất cả khách hàng",
    CUSTOMER_SEGMENT: "Nhóm khách hàng",
    SPECIFIC_CUSTOMERS: "Khách hàng cụ thể"
};

const eventStatusLabels: Record<string, string> = {
    DRAFT: "Bản nháp",
    SCHEDULED: "Đã lên lịch",
    ACTIVE: "Đang hoạt động",
    PAUSED: "Tạm dừng",
    EXPIRED: "Đã hết hạn",
    ENDED: "Đã kết thúc",
    CANCELLED: "Đã hủy"
};

export function getEventTypeLabel(value?: string) {
    if (!value) return "-";

    return eventTypeLabels[value] ?? formatFallbackEnum(value);
}

export function getEventAudienceLabel(value?: string) {
    if (!value) return "-";

    return eventAudienceLabels[value] ?? formatFallbackEnum(value);
}

export function getEventStatusLabel(value?: string) {
    if (!value) return "-";

    return eventStatusLabels[value] ?? formatFallbackEnum(value);
}

export function formatFallbackEnum(value: string) {
    return value
        .toLowerCase()
        .split("_")
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join(" ");
}

/** Màu huy hiệu dùng chung cho trạng thái sự kiện, khuyến mãi, coupon và lượt dùng coupon. */
const marketingStatusTones: Record<string, BadgeTone> = {
    DRAFT: "neutral",
    SCHEDULED: "info",
    ACTIVE: "success",
    PAUSED: "warning",
    EXPIRED: "pending",
    ENDED: "neutral",
    CANCELLED: "danger",
    ARCHIVED: "neutral",
    RESERVED: "info",
    APPLIED: "success",
    RELEASED: "warning"
};

export function getMarketingStatusTone(value?: string): BadgeTone {
    return (value && marketingStatusTones[value]) || "neutral";
}
