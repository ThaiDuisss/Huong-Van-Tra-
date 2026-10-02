import type { BundlePaymentStatus, BundleReservationStatus,  CustomBundleStatus } from "../types/custom-bundle.types";

export const customBundleStatusLabels: Record<CustomBundleStatus, string> = {
    AWAITING_PAYMENT: "Chờ thanh toán",
    AWAITING_RESERVATION: "Chờ giữ nguyên liệu",
    CANCELLED: "Đã hủy",
    DRAFT: "Nháp",
    FAILED: "Lỗi",
    PACKED: "Đã đóng gói",
    PACKING: "Đang đóng gói",
    PAID: "Đã thanh toán",
    RESERVATION_FAILED: "Giữ nguyên liệu thất bại",
    RESERVED: "Đã giữ nguyên liệu"
};

export const bundlePaymentStatusLabels: Record<BundlePaymentStatus, string> = {
    FAILED: "Thanh toán thất bại",
    PENDING: "Chờ thanh toán",
    REFUND_REQUIRED: "Cần hoàn tiền",
    SUCCESS: "Đã thanh toán"
};

export const bundleReservationLabels: Record<BundleReservationStatus, string> = {
    COMMITTED: "Đã commit",
    EXPIRED: "Hết hạn",
    FAILED: "Thất bại",
    NONE: "Chưa giữ",
    RELEASED: "Đã release",
    RESERVED: "Đã giữ"
};

export const customBundleRoleLabels: Record<string, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    MANAGER: "Quản lý chi nhánh",
    INVENTORY_STAFF: "Nhân viên kho",
    QC_STAFF: "Nhân viên QC",
    SALES_STAFF: "Nhân viên bán hàng"
};

export const maxBundleWeight = 1000;
