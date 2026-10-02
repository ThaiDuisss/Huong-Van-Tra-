import type {
    TransferDiscrepancyResolutionStatus,
    TransferDiscrepancyType,
    TransferListTab,
    TransferPriority,
    
    TransferStatus
} from "../types/transfer.types";

export const transferStatusLabels: Record<TransferStatus, string> = {
    APPROVED: "Đã duyệt",
    CANCELLED: "Đã hủy",
    DRAFT: "Nháp",
    PARTIALLY_RECEIVED: "Nhận có chênh lệch",
    PICKING: "Đang picking",
    RECEIVED: "Hoàn thành",
    REJECTED: "Từ chối",
    REQUESTED: "Chờ duyệt",
    RESERVED: "Đã giữ tồn",
    SHIPPED: "Đang vận chuyển"
};

export const transferRoleLabels: Record< string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    BRANCH_MANAGER: "Quản lý chi nhánh",
    DIRECTOR: "Giám đốc",
    INVENTORY_STAFF: "Nhân viên kho",
    SALES_STAFF: "Nhân viên bán hàng"
};

export const transferPriorityLabels: Record<TransferPriority, string> = {
    HIGH: "Cao",
    LOW: "Thấp",
    NORMAL: "Bình thường",
    URGENT: "Khẩn"
};

export const transferTabLabels: Record<TransferListTab, string> = {
    ALL: "Tất cả",
    AWAITING_RECEIVE: "Chờ nhận",
    COMPLETED: "Hoàn thành",
    DISCREPANCY: "Có chênh lệch",
    IN_TRANSIT: "Đang vận chuyển",
    PREPARING: "Đang chuẩn bị",
    REQUESTED: "Chờ duyệt"
};

export const transferDiscrepancyTypeLabels: Record<
    TransferDiscrepancyType,
    string
> = {
    DAMAGE: "Hỏng",
    EXTRA: "Thừa",
    SHORTAGE: "Thiếu",
    WRONG_BATCH: "Sai batch",
    WRONG_SKU: "Sai SKU"
};

export const transferDiscrepancyResolutionLabels: Record<
    TransferDiscrepancyResolutionStatus,
    string
> = {
    INVESTIGATING: "Đang xác minh",
    OPEN: "Mở",
    RESOLVED: "Đã xử lý"
};

export const transferStatusOptions: TransferStatus[] = [
    "DRAFT",
    "REQUESTED",
    "APPROVED",
    "RESERVED",
    "PICKING",
    "SHIPPED",
    "PARTIALLY_RECEIVED",
    "RECEIVED",
    "REJECTED",
    "CANCELLED"
];

export const transferPriorityOptions: TransferPriority[] = [
    "LOW",
    "NORMAL",
    "HIGH",
    "URGENT"
];

export const transferTabs: TransferListTab[] = [
    "ALL",
    "REQUESTED",
    "PREPARING",
    "IN_TRANSIT",
    "AWAITING_RECEIVE",
    "DISCREPANCY",
    "COMPLETED"
];
