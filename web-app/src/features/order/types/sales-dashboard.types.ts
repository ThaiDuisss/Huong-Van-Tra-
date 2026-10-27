import type { TopSalesStaffRow, TopSellingProductRow } from "../../report/types/statistic-report.types";

/** `GET /orders/reports/sales-dashboard` — số liệu tổng hợp cho dashboard bán hàng. */
export interface SalesDashboardHourlyPoint {
  hour: number;
  /** `HH:00` */
  label: string;
  orderCount: number;
  revenue: number | string;
}

export interface SalesDashboardPaymentRow {
  /** `CASH`, `BANK_TRANSFER`, ... hoặc rỗng khi đơn chưa ghi phương thức. */
  method: string | null;
  orderCount: number;
  revenue: number | string;
}

export interface SalesDashboardStoreRow {
  branchId: string | null;
  branchName: string | null;
  orderCount: number;
  revenue: number | string;
  averageOrderValue: number | string;
}

export interface SalesDashboardResponse {
  fromDate: string;
  toDate: string;
  totalOrders: number;
  totalRevenue: number | string;
  hourlyTrend: SalesDashboardHourlyPoint[];
  paymentBreakdown: SalesDashboardPaymentRow[];
  storePerformance: SalesDashboardStoreRow[];
  topProducts: TopSellingProductRow[];
  topStaff: TopSalesStaffRow[];
}
