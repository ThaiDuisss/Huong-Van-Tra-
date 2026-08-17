import type {
    ProductionOrderStatus,
    ProductionPriority,
    ProductionRole
} from "../types/production-order.types";

export const productionStatusLabels: Record<ProductionOrderStatus, string> = {
    CANCELLED: "Đã hủy",
    COMPLETED: "Hoàn thành",
    DRAFT: "Nháp",
    FAILED: "Thất bại",
    IN_PROGRESS: "Đang sản xuất",
    PARTIALLY_COMPLETED: "Hoàn thành một phần",
    PENDING_APPROVAL: "Chờ duyệt",
    RELEASED: "Đã giữ nguyên liệu"
};

export const productionPriorityLabels: Record<ProductionPriority, string> = {
    HIGH: "Cao",
    LOW: "Thấp",
    NORMAL: "Bình thường",
    URGENT: "Khẩn cấp"
};

export const productionRoleLabels: Record<ProductionRole, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    BRANCH_MANAGER: "Quản lý chi nhánh",
    DIRECTOR: "Giám đốc",
    QC_STAFF: "Nhân viên QC",
    WAREHOUSE: "Nhân viên kho"
};

export const productionStatusOptions: ProductionOrderStatus[] = [
    "DRAFT",
    "PENDING_APPROVAL",
    "RELEASED",
    "IN_PROGRESS",
    "PARTIALLY_COMPLETED",
    "COMPLETED",
    "CANCELLED",
    "FAILED"
];

export const productionPriorityOptions: ProductionPriority[] = [
    "LOW",
    "NORMAL",
    "HIGH",
    "URGENT"
];

export const productionCompletionTolerancePercent = 5;
