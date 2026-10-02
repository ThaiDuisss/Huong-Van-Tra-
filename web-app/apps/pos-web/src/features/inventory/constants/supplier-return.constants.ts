import type {
    SupplierReturnReason,
    SupplierReturnResolution,
    SupplierReturnStatus
} from "../types/supplier-return.types";

export const supplierReturnStatusLabels: Record<SupplierReturnStatus, string> = {
    APPROVED: "Đã duyệt",
    CANCELLED: "Đã hủy",
    COMPLETED: "Hoàn tất",
    CREDIT_PENDING: "Chờ credit note",
    DRAFT: "Nháp",
    PREPARING: "Đang chuẩn bị",
    REJECTED: "Từ chối",
    REPLACEMENT_PENDING: "Chờ hàng thay thế",
    SHIPPED: "Đã gửi NCC",
    SUBMITTED: "Chờ duyệt"
};

export const supplierReturnReasonLabels: Record<SupplierReturnReason, string> = {
    "SUP-01": "Không đạt chất lượng",
    "SUP-02": "Sai hàng",
    "SUP-03": "Sai số lượng",
    "SUP-04": "Bao bì hỏng",
    "SUP-05": "Gần hết hạn",
    "SUP-06": "Sai lot/chứng từ",
    "SUP-99": "Khác"
};

export const supplierReturnResolutionLabels: Record<SupplierReturnResolution, string> = {
    CREDIT_NOTE: "Credit note",
    REFUND: "Refund",
    REPLACEMENT: "Hàng thay thế"
};

export const supplierReturnRoleLabels: Record<string, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    DIRECTOR: "Giám đốc",
    INVENTORY_STAFF: "Nhân viên kho",
    QC_STAFF: "Nhân viên QC"
};

export const supplierReturnReasons = Object.keys(supplierReturnReasonLabels) as SupplierReturnReason[];
export const supplierReturnStatuses = Object.keys(supplierReturnStatusLabels) as SupplierReturnStatus[];
