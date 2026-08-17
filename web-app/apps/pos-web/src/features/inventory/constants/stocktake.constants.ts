import type {
    StocktakeCondition,
    StocktakeReasonCode,
    StocktakeRole,
    StocktakeStatus,
    StocktakeType,
    StocktakeVarianceLevel
} from "../types/stocktake.types";

export const stocktakeStatusLabels: Record<StocktakeStatus, string> = {
    APPROVED: "Đã duyệt",
    CANCELLED: "Đã hủy",
    COUNTED: "Đã đếm",
    DRAFT: "Nháp",
    IN_PROGRESS: "Đang kiểm kê",
    PENDING_APPROVAL: "Chờ duyệt",
    POSTED: "Đã post",
    RECONCILING: "Đang đối chiếu"
};

export const stocktakeTypeLabels: Record<StocktakeType, string> = {
    BATCH_SELECTED: "Theo batch",
    CATEGORY: "Theo danh mục",
    CYCLE_COUNT: "Cycle count",
    FULL: "Toàn kho",
    SKU_SELECTED: "Theo SKU"
};

export const stocktakeRoleLabels: Record<StocktakeRole, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    BRANCH_MANAGER: "Quản lý chi nhánh",
    DIRECTOR: "Giám đốc",
    SALES_STAFF: "Nhân viên bán hàng",
    WAREHOUSE: "Nhân viên kho"
};

export const stocktakeConditionLabels: Record<StocktakeCondition, string> = {
    DAMAGED: "Hỏng",
    EXPIRED: "Hết hạn",
    GOOD: "Tốt",
    UNKNOWN: "Không rõ"
};

export const stocktakeReasonLabels: Record<StocktakeReasonCode, string> = {
    "ADJ-01": "Hao hụt tự nhiên",
    "ADJ-02": "Hỏng/mốc",
    "ADJ-03": "Mất/thất thoát",
    "ADJ-04": "Sai chứng từ",
    "ADJ-05": "Hao hụt sản xuất",
    "ADJ-06": "Hàng nhận chưa ghi nhận",
    "ADJ-07": "Sai đơn vị quy đổi",
    "ADJ-08": "Chênh lệch chuyển kho",
    "ADJ-09": "Lỗi đồng bộ",
    "ADJ-99": "Khác"
};

export const stocktakeVarianceLevelLabels: Record<StocktakeVarianceLevel, string> = {
    LARGE: "Chênh lệch lớn",
    MEDIUM: "Chênh lệch trung bình",
    SMALL: "Chênh lệch nhỏ"
};

export const stocktakeStatusOptions: StocktakeStatus[] = [
    "DRAFT",
    "IN_PROGRESS",
    "COUNTED",
    "RECONCILING",
    "PENDING_APPROVAL",
    "APPROVED",
    "POSTED",
    "CANCELLED"
];

export const stocktakeTypeOptions: StocktakeType[] = [
    "FULL",
    "CYCLE_COUNT",
    "CATEGORY",
    "SKU_SELECTED",
    "BATCH_SELECTED"
];

export const stocktakeReasonOptions = Object.keys(stocktakeReasonLabels) as StocktakeReasonCode[];

export const stocktakeApprovalThresholds = {
    large: 5000000,
    medium: 1500000
};
