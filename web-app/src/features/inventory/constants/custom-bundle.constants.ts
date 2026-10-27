import type { CustomBundlePackingStatus } from "../types/custom-bundle.types";

export const customBundlePackingStatusLabels: Record<CustomBundlePackingStatus, string> = {
    WAITING_MATERIAL: "Chờ nguyên liệu",
    AWAITING_PACKING: "Chờ đóng gói",
    PACKED: "Đã đóng gói",
    CANCELLED: "Đã huỷ"
};

export const customBundlePackingStatusTones: Record<CustomBundlePackingStatus, string> = {
    WAITING_MATERIAL: "bg-amber-50 text-amber-800",
    AWAITING_PACKING: "bg-sky-50 text-sky-800",
    PACKED: "bg-[#eaf7ec] text-[#159734]",
    CANCELLED: "bg-neutral-100 text-neutral-600"
};

/** Thứ tự tab ở hàng đợi đóng gói của kho. */
export const customBundlePackingTabs: CustomBundlePackingStatus[] = [
    "AWAITING_PACKING",
    "WAITING_MATERIAL",
    "PACKED",
    "CANCELLED"
];

export const customBundlePackagingTypes = ["Hộp giấy kraft", "Túi zip bạc", "Hộp thiếc"];
