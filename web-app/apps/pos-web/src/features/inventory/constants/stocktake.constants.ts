import type {
  AdjustmentStatus,
  StocktakeScopeType,
  StocktakeStatus,
} from "../types/stocktake.types";

export const stocktakeStatusLabels: Record<StocktakeStatus, string> = {
  APPROVED: "Đã duyệt",
  CANCELLED: "Đã hủy",
  COMPLETED: "Hoàn tất",
  COUNTING: "Đang kiểm kê",
  DRAFT: "Chưa bắt đầu",
  FROZEN: "Đã xác nhận phạm vi",
  PENDING_APPROVAL: "Đang chờ quản lý duyệt",
  POSTED: "Đã cập nhật tồn kho",
  RECONCILING: "Đang kiểm tra chênh lệch",
  REJECTED: "Đã từ chối",
  RETURNED: "Cần kiểm kê lại",
};

export const stocktakeScopeLabels: Record<StocktakeScopeType, string> = {
  FULL: "Toàn bộ kho",
  LOCATION: "Theo vị trí",
  PARTIAL: "Theo mặt hàng",
};

export const adjustmentStatusLabels: Record<AdjustmentStatus, string> = {
  APPROVED: "Đã duyệt",
  DRAFT: "Chờ duyệt",
  POSTED: "Đã cập nhật tồn kho",
  REJECTED: "Đã từ chối",
};

export const stocktakeStatusOptions: StocktakeStatus[] = [
  "DRAFT",
  "FROZEN",
  "COUNTING",
  "RECONCILING",
  "PENDING_APPROVAL",
  "RETURNED",
  "REJECTED",
  "APPROVED",
  "POSTED",
  "COMPLETED",
  "CANCELLED",
];
export const stocktakeScopeOptions: StocktakeScopeType[] = ["FULL", "LOCATION", "PARTIAL"];
