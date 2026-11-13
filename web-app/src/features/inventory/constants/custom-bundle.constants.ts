import type { BadgeTone } from "../../../components/ui/StatusBadge";
import type { CustomBundlePackingStatus } from "../types/custom-bundle.types";

export const customBundlePackingStatusLabels: Record<CustomBundlePackingStatus, string> = {
    WAITING_MATERIAL: "Chờ nguyên liệu",
    AWAITING_PACKING: "Chờ đóng gói",
    PACKED: "Đã đóng gói",
    CANCELLED: "Đã huỷ"
};

/** Màu `StatusBadge` theo trạng thái đóng gói. */
export const customBundlePackingStatusTones: Record<CustomBundlePackingStatus, BadgeTone> = {
    WAITING_MATERIAL: "warning",
    AWAITING_PACKING: "sky",
    PACKED: "success",
    CANCELLED: "neutral"
};

/** Thứ tự tab ở hàng đợi đóng gói của kho. */
export const customBundlePackingTabs: CustomBundlePackingStatus[] = [
    "AWAITING_PACKING",
    "WAITING_MATERIAL",
    "PACKED",
    "CANCELLED"
];

export const customBundlePackagingTypes = ["Hộp giấy kraft", "Túi zip bạc", "Hộp thiếc"];
