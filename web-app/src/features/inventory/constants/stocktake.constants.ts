import type {
  AdjustmentStatus,
  StocktakeScopeType,
  StocktakeStatusFilter,
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

export const stocktakeStatusFilterOptions: Array<{
  label: string;
  statuses: StocktakeStatus[];
  value: Exclude<StocktakeStatusFilter, "ALL">;
}> = [
  { label: "Chuẩn bị", statuses: ["DRAFT", "FROZEN"], value: "PREPARING" },
  {
    label: "Đang xử lý",
    statuses: ["COUNTING", "RECONCILING", "RETURNED"],
    value: "IN_PROGRESS",
  },
  { label: "Chờ duyệt", statuses: ["PENDING_APPROVAL"], value: "PENDING_APPROVAL" },
  { label: "Chờ cập nhật tồn", statuses: ["APPROVED"], value: "APPROVED" },
  { label: "Hoàn tất", statuses: ["POSTED", "COMPLETED"], value: "COMPLETED" },
  { label: "Đã dừng", statuses: ["REJECTED", "CANCELLED"], value: "CANCELLED" },
];
export const stocktakeScopeOptions: StocktakeScopeType[] = ["FULL", "LOCATION", "PARTIAL"];
