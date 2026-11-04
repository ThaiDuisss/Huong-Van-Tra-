import type {
  AdjustmentStatus,
  StocktakeScopeType,
  StocktakeStatus,
} from "../types/stocktake.types";

export const stocktakeStatusLabels: Record<StocktakeStatus, string> = {
  CANCELLED: "Đã hủy",
  COMPLETED: "Hoàn tất",
  COUNTING: "Đang kiểm kê",
  DRAFT: "Chưa bắt đầu",
  PENDING_APPROVAL: "Chờ duyệt",
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
  value: StocktakeStatus;
}> = [
  { label: "Chưa bắt đầu", value: "DRAFT" },
  { label: "Đang kiểm kê", value: "COUNTING" },
  { label: "Chờ duyệt", value: "PENDING_APPROVAL" },
  { label: "Hoàn tất", value: "COMPLETED" },
  { label: "Đã hủy", value: "CANCELLED" },
];
export const stocktakeScopeOptions: StocktakeScopeType[] = ["FULL", "LOCATION", "PARTIAL"];
