import type {
  InventoryBalanceViewResponse,
  InventoryTransactionResponse,
} from "../../inventory/types/inventory.types";
import type { QualityBatchSummary } from "../../inventory/types/quality.types";
import type { StockAlert } from "../../inventory/types/stock-alert.types";
import type { StockDocumentSummary } from "../../inventory/types/stock-document.types";
import type { OrderStatsResponse } from "../../order/types/order.types";
import type {
  SalesHistoryOrder,
  SalesHistorySummary,
} from "../../order/types/sales-history.types";

export type DashboardTimeRange = "TODAY" | "SEVEN_DAYS" | "THIRTY_DAYS";

export interface DashboardFilters {
  timeRange: DashboardTimeRange;
  locationId: string;
}

export type DashboardTab = "OVERVIEW" | "POS" | "INVENTORY" | "PRODUCTION" | "ALERTS";

/** Nguồn dữ liệu của một widget. REAL/ADAPTED từ API thật, DEMO từ mock FE, GAP chưa có API. */
export type DashboardSourceStatus = "REAL" | "ADAPTED" | "DEMO" | "GAP";

/** Một phần dữ liệu trong snapshot, lỗi được cô lập theo phần. */
export interface DashboardPart<T> {
  data: T | null;
  error?: string;
}

export type DashboardResource<T> =
  | { status: "idle" }
  | { status: "loading"; data?: T }
  | { status: "ready"; data: T; loadedAt: number }
  | { status: "error"; message: string; data?: T };

export interface DashboardLocationSummary {
  locationId: string;
  locationName: string;
  onHand: number;
  reserved: number;
  blocked: number;
  available: number;
}

export interface DashboardPendingDeduction {
  id: string;
  referenceCode: string;
  skuCode: string;
  locationName: string;
  batchCode: string;
  quantity: number;
  createdAt: string;
  source: string;
  status: "PENDING" | "RETRYING" | "FAILED";
}

export interface DashboardExpiringReservation {
  id: string;
  code: string;
  skuCode: string;
  locationName: string;
  batchCode?: string;
  reservedQuantity: number;
  reservedFor: string;
  expiresAt: string;
  status: "RESERVED" | "CONFIRMED" | "EXPIRED";
}

export interface DashboardTrendPoint {
  label: string;
  value: number;
  secondaryValue?: number;
  source: "REAL" | "ADAPTED" | "MOCK" | "GAP";
}

export interface DashboardSkuPerformance {
  id: string;
  skuCode: string;
  skuName: string;
  category: string;
  quantity: number;
  revenue: number;
  source: "MOCK" | "GAP";
}

export interface DashboardPaymentBreakdown {
  method: string;
  label: string;
  transactionCount: number;
  revenue: number;
  status: "MOCK" | "GAP";
}

export interface DashboardStorePerformance {
  id: string;
  storeName: string;
  revenue: number;
  orderCount: number;
  averageOrderValue: number;
  source: "MOCK" | "GAP";
}

export interface DashboardStaffPerformance {
  id: string;
  staffName: string;
  revenue: number;
  orderCount: number;
  cancelRefundRate: number;
  source: "MOCK" | "GAP";
}

export interface DashboardProductionWastage {
  id: string;
  productionOrderCode: string;
  materialCode: string;
  materialName: string;
  plannedQuantity: number;
  actualQuantity: number;
  wastageQuantity: number;
  wastageRate: number;
  thresholdRate: number;
  status: "NORMAL" | "WATCH" | "OVER_THRESHOLD";
  source: "MOCK" | "GAP";
}

/** Dữ liệu demo chỉ tồn tại khi bật mock (dev). Production nhận null → widget hiển thị GAP. */
export interface DashboardSalesDemo {
  revenueTrend: DashboardTrendPoint[];
  orderTrend: DashboardTrendPoint[];
  topSkus: DashboardSkuPerformance[];
  paymentBreakdown: DashboardPaymentBreakdown[];
  storePerformance: DashboardStorePerformance[];
  staffPerformance: DashboardStaffPerformance[];
}

export interface DashboardSalesSnapshot {
  timeRange: DashboardTimeRange;
  fromDate: string;
  toDate: string;
  /** `/orders/today-sales/summary` — chỉ có dữ liệu hôm nay. */
  todaySummary: DashboardPart<SalesHistorySummary>;
  /** `/orders/stats` theo khoảng thời gian đã chọn. */
  stats: DashboardPart<OrderStatsResponse>;
  /** `/orders/today-sales` trang đầu. */
  recentOrders: DashboardPart<{ items: SalesHistoryOrder[]; total: number }>;
  demo: DashboardSalesDemo | null;
}

export interface DashboardInventorySnapshot {
  locationId: string;
  balances: DashboardPart<{
    items: InventoryBalanceViewResponse[];
    loaded: number;
    total: number;
  }>;
  transactions: DashboardPart<InventoryTransactionResponse[]>;
}

export interface DashboardAlertSnapshot {
  /** Các service FE hiện có (in-memory mock) — gắn nhãn DEMO. */
  stockAlerts: DashboardPart<StockAlert[]>;
  expiringBatches: DashboardPart<QualityBatchSummary[]>;
  pendingDocuments: DashboardPart<StockDocumentSummary[]>;
  /** Chỉ có khi bật mock dashboard; null = GAP. */
  pendingDeductions: DashboardPendingDeduction[] | null;
  expiringReservations: DashboardExpiringReservation[] | null;
}

export interface DashboardProductionSnapshot {
  /** null = GAP (chưa có API và mock tắt). */
  wastage: DashboardProductionWastage[] | null;
}

export type DashboardAlertSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type DashboardAlertKind =
  | "REORDER"
  | "STOCK_ALERT"
  | "EXPIRY"
  | "PENDING_DEDUCTION"
  | "RESERVATION"
  | "PENDING_APPROVAL";

export interface DashboardAlertItem {
  id: string;
  kind: DashboardAlertKind;
  kindLabel: string;
  severity: DashboardAlertSeverity;
  title: string;
  subject: string;
  occurredAt: string;
  source: DashboardSourceStatus;
  /** Route drill-down; undefined khi chưa có màn chi tiết. */
  to?: string;
}
