import type {
    AdjustmentStatus,
    StocktakeRole,
    StocktakeScopeType,
    StocktakeStatus
} from "../types/stocktake.types";

export const stocktakeStatusLabels: Record<StocktakeStatus, string> = {
    COMPLETED: "Hoàn tất",
    COUNTING: "Đang kiểm kê",
    DRAFT: "Nháp"
};

export const stocktakeScopeLabels: Record<StocktakeScopeType, string> = {
    FULL: "Toàn bộ kho",
    LOCATION: "Theo vị trí",
    PARTIAL: "Theo danh sách"
};

export const stocktakeRoleLabels: Record<StocktakeRole, string> = {
    ACCOUNTANT: "Kế toán",
    ADMIN: "Quản trị viên",
    BRANCH_MANAGER: "Quản lý chi nhánh",
    DIRECTOR: "Giám đốc",
    SALES_STAFF: "Nhân viên bán hàng",
    WAREHOUSE: "Nhân viên kho"
};

export const adjustmentStatusLabels: Record<AdjustmentStatus, string> = {
    APPROVED: "Đã duyệt",
    DRAFT: "Nháp",
    POSTED: "Đã hạch toán",
    REJECTED: "Đã từ chối"
};

export const stocktakeStatusOptions: StocktakeStatus[] = ["DRAFT", "COUNTING", "COMPLETED"];
export const stocktakeScopeOptions: StocktakeScopeType[] = ["FULL", "LOCATION", "PARTIAL"];
