import { ROLE_LABELS } from "../../../auth/roles";
import type {
    InventoryActionType,
    InventoryDocumentType,
    InventoryRecordStatus
} from "../types/inventory-management.types";

/** Nhãn vai trò dùng chung của hệ thống. */
export const inventoryRoleLabels: Record<string, string> = ROLE_LABELS;

export const inventoryStatusLabels: Record<InventoryRecordStatus, string> = {
    DRAFT: "Nháp",
    PENDING_APPROVAL: "Chờ duyệt",
    APPROVED: "Đã duyệt",
    IN_PROGRESS: "Đang xử lý",
    COMPLETED: "Hoàn tất",
    REJECTED: "Từ chối",
    CANCELLED: "Đã hủy",
    WARNING: "Cần chú ý",
    BLOCKED: "Tạm khóa"
};

export const inventoryDocumentTypeLabels: Record<
    InventoryDocumentType,
    string
> = {
    STOCK_RECEIPT: "Phiếu nhập kho",
    PRODUCTION_ORDER: "Lệnh sản xuất",
    BUNDLE_ORDER: "Phiếu phối bundle",
    TRANSFER_ORDER: "Phiếu chuyển kho",
    STOCK_COUNT: "Phiếu kiểm kê",
    CUSTOMER_RETURN: "Phiếu khách trả hàng",
    SUPPLIER_RETURN: "Phiếu trả nhà cung cấp",
    DISPOSAL: "Phiếu tiêu hủy",
    REPLENISHMENT: "Đề xuất bổ sung"
};

export const inventoryActionLabels: Record<InventoryActionType, string> = {
    CREATE: "Tạo mới",
    APPROVE: "Duyệt",
    COMPLETE: "Hoàn tất",
    CANCEL: "Hủy",
    EXPORT: "Xuất dữ liệu"
};

export const inventoryStatusOptions: Array<
    "ALL" | InventoryRecordStatus
> = [
    "ALL",
    "DRAFT",
    "PENDING_APPROVAL",
    "APPROVED",
    "IN_PROGRESS",
    "COMPLETED",
    "WARNING",
    "BLOCKED",
    "CANCELLED"
];

export const inventoryReasonCodeLabels: Record<string, string> = {
    SUPPLIER_DELIVERY: "Nhập từ nhà cung cấp",
    PRODUCTION_USAGE: "Cấp phát sản xuất",
    CUSTOMER_RETURN: "Khách trả hàng",
    EXPIRED: "Hết hạn",
    DAMAGED: "Hàng hỏng",
    STOCK_COUNT_DIFF: "Chênh lệch kiểm kê",
    REPLENISHMENT: "Bổ sung dưới định mức"
};
