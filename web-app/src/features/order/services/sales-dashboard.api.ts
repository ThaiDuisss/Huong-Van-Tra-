import { apiClient } from "../../../api/api-client";
import type { SalesDashboardResponse } from "../types/sales-dashboard.types";

/** Tối đa 31 ngày (backend từ chối kỳ dài hơn). Thiếu ngày thì backend lấy hôm nay. */
export function getSalesDashboard(params: { fromDate?: string; toDate?: string }) {
  return apiClient.get<SalesDashboardResponse>("/orders/reports/sales-dashboard", { query: params });
}
