import type {
    StockBatchStatus,
    StockDocumentAction,
    StockDocumentDirection,
    StockDocumentLineStatus,
    
    StockDocumentStatus,
    StockDocumentType
} from "../types/stock-document.types";

export const stockDocumentTypeLabels: Record<StockDocumentType, string> = {
    ADJUSTMENT_IN: "Điều chỉnh tăng",
    ADJUSTMENT_OUT: "Điều chỉnh giảm",
    CUSTOMER_RETURN_RECEIPT: "Nhập khách trả",
    CUSTOM_BUNDLE_ISSUE: "Xuất custom bundle",
    PRODUCTION_ISSUE: "Xuất sản xuất",
    PRODUCTION_RECEIPT: "Nhập sản xuất",
    PURCHASE_RECEIPT: "Nhập mua hàng",
    SALE_ISSUE: "Xuất bán hàng",
    SUPPLIER_RETURN: "Trả nhà cung cấp",
    TRANSFER_ISSUE: "Xuất chuyển kho",
    TRANSFER_RECEIPT: "Nhập chuyển kho",
    WRITE_OFF: "Tiêu hủy"
};

export const stockDocumentStatusLabels: Record<StockDocumentStatus, string> = {
    APPROVED: "Đã duyệt",
    CANCELLED: "Đã hủy",
    DRAFT: "Nháp",
    POSTED: "Đã post",
    REJECTED: "Từ chối",
    REVERSED: "Đã đảo",
    SUBMITTED: "Chờ duyệt"
};

export const stockDocumentRoleLabels: Record< string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    BRANCH_MANAGER: "Quản lý chi nhánh",
    DIRECTOR: "Giám đốc",
    INVENTORY_STAFF: "Nhân viên kho",
    QC_STAFF: "Nhân viên QC",
    SALES_STAFF: "Nhân viên bán hàng"
};

export const stockDocumentActionLabels: Record<StockDocumentAction, string> = {
    APPROVE: "Approve",
    CANCEL: "Cancel",
    POST: "Post",
    REJECT: "Reject",
    REVERSE: "Tạo yêu cầu đảo",
    SUBMIT: "Submit"
};

export const stockDocumentLineStatusLabels: Record<
    StockDocumentLineStatus,
    string
> = {
    POSTED: "Đã post",
    QC_HOLD: "Chờ QC",
    VALID: "Hợp lệ",
    WAITING_BATCH: "Chờ phân bổ lô"
};

export const stockBatchStatusLabels: Record<StockBatchStatus, string> = {
    ALLOCATED: "Đã phân bổ",
    AVAILABLE: "Sẵn sàng",
    DAMAGED: "Hàng hỏng",
    EXPIRED: "Hết hạn",
    NEAR_EXPIRY: "Sắp hết hạn"
};

export const stockDocumentTypeOptions: StockDocumentType[] = [
    "PURCHASE_RECEIPT",
    "PRODUCTION_RECEIPT",
    "CUSTOMER_RETURN_RECEIPT",
    "TRANSFER_RECEIPT",
    "ADJUSTMENT_IN",
    "SALE_ISSUE",
    "PRODUCTION_ISSUE",
    "CUSTOM_BUNDLE_ISSUE",
    "TRANSFER_ISSUE",
    "SUPPLIER_RETURN",
    "ADJUSTMENT_OUT",
    "WRITE_OFF"
];

export const stockDocumentStatusOptions: StockDocumentStatus[] = [
    "DRAFT",
    "SUBMITTED",
    "APPROVED",
    "POSTED",
    "REJECTED",
    "CANCELLED",
    "REVERSED"
];

export const stockDocumentDirectionLabels: Record<
    StockDocumentDirection,
    string
> = {
    ALL: "Tất cả",
    IN: "Nhóm nhập",
    OUT: "Nhóm xuất"
};

export const inboundStockDocumentTypes: StockDocumentType[] = [
    "PURCHASE_RECEIPT",
    "PRODUCTION_RECEIPT",
    "CUSTOMER_RETURN_RECEIPT",
    "TRANSFER_RECEIPT",
    "ADJUSTMENT_IN"
];

export const outboundStockDocumentTypes: StockDocumentType[] = [
    "SALE_ISSUE",
    "PRODUCTION_ISSUE",
    "CUSTOM_BUNDLE_ISSUE",
    "TRANSFER_ISSUE",
    "SUPPLIER_RETURN",
    "ADJUSTMENT_OUT",
    "WRITE_OFF"
];

export const stockBusinessCreationLinks = [
    {
        label: "Nhập nguyên liệu",
        to: "/system/inventory/raw-material-receipts"
    },
    {
        label: "Tạo lệnh sản xuất",
        to: "/system/inventory/production-orders"
    },
    {
        label: "Chuyển kho",
        to: "/system/inventory/branch-transfers"
    },
    {
        label: "Kiểm kê",
        to: "/system/inventory/stock-counts"
    },
    {
        label: "Trả nhà cung cấp",
        to: "/system/inventory/supplier-returns"
    }
];

export function isInboundDocument(type: StockDocumentType) {
    return inboundStockDocumentTypes.includes(type);
}
