import type {
    StockAlertRole,
    StockAlertSeverity,
    StockAlertStatus,
    StockAlertType,
    SuggestedActionType
} from "../types/stock-alert.types";

export const stockAlertTypeLabels: Record<StockAlertType, string> = {
    BELOW_MINIMUM: "Dưới tồn tối thiểu",
    BELOW_REORDER_POINT: "Dưới điểm đặt lại",
    EXPIRING_SOON: "Sắp hết hạn",
    HIGH_RESERVATION: "Reservation cao",
    NEGATIVE_AVAILABLE_TO_PROMISE: "ATP âm",
    OUT_OF_STOCK: "Hết hàng",
    PURCHASE_RECOMMENDED: "Đề xuất mua hàng",
    TRANSFER_RECOMMENDED: "Đề xuất chuyển kho"
};

export const stockAlertStatusLabels: Record<StockAlertStatus, string> = {
    ACKNOWLEDGED: "Đã xác nhận",
    ACTION_CREATED: "Đã tạo action",
    DISMISSED: "Đã bỏ qua",
    OPEN: "Đang mở",
    RESOLVED: "Đã xử lý"
};

export const stockAlertSeverityLabels: Record<StockAlertSeverity, string> = {
    CRITICAL: "Nghiêm trọng",
    HIGH: "Cao",
    LOW: "Thấp",
    MEDIUM: "Trung bình"
};

export const suggestedActionLabels: Record<SuggestedActionType, string> = {
    NONE: "Theo dõi",
    PURCHASE: "Tạo yêu cầu mua hàng",
    TRANSFER: "Tạo yêu cầu chuyển kho"
};

export const stockAlertRoleLabels: Record<StockAlertRole, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    BRANCH_MANAGER: "Quản lý chi nhánh",
    DIRECTOR: "Giám đốc",
    INVENTORY_STAFF: "Nhân viên kho",
    SALES_STAFF: "Nhân viên bán hàng"
};

export const stockAlertTypes = Object.keys(stockAlertTypeLabels) as StockAlertType[];
export const stockAlertStatuses = Object.keys(stockAlertStatusLabels) as StockAlertStatus[];
export const stockAlertSeverities = Object.keys(stockAlertSeverityLabels) as StockAlertSeverity[];
