import type { InventoryDashboardSummaryResponse } from "../../inventory/services/inventoryDashboard.api";
import type {
  InventoryBalanceViewResponse,
  InventoryTransactionResponse,
} from "../../inventory/types/inventory.types";
import type { QualityBatchSummary } from "../../inventory/types/quality.types";
import type { StockAlert } from "../../inventory/types/stock-alert.types";
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

/** Phiếu nhập kho đang chờ phê duyệt (từ `/inventory/goods-receipts?status=PENDING_APPROVAL`). */
export interface DashboardPendingDocument {
  id: string;
  code: string;
  typeLabel: string;
  locationName: string | null;
  supplierName: string | null;
  createdAt: string | null;
}

export interface DashboardSkuPerformance {
  id: string;
  skuCode: string;
  skuName: string;
  category: string;
  quantity: number;
  revenue: number;
  source: "REAL" | "MOCK" | "GAP";
}

export interface DashboardPaymentBreakdown {
  method: string;
  label: string;
  transactionCount: number;
  revenue: number;
  status: "REAL" | "MOCK" | "GAP";
}

export interface DashboardStorePerformance {
  id: string;
  storeName: string;
  revenue: number;
  orderCount: number;
  averageOrderValue: number;
  source: "REAL" | "MOCK" | "GAP";
}

export interface DashboardStaffPerformance {
  id: string;
  staffName: string;
  revenue: number;
  orderCount: number;
  cancelRefundRate: number;
  source: "REAL" | "MOCK" | "GAP";
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
  source: DashboardSourceStatus;
}

/**
 * Số liệu phân tích bán hàng từ `/orders/reports/sales-dashboard` (REAL). Khi bật mock dashboard
 * và API lỗi thì thay bằng dữ liệu MOCK; lỗi mà không có mock → `data: null`, widget hiển thị GAP.
 */
export interface DashboardSalesAnalytics {
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
  /** `/orders/reports/sales-dashboard` theo khoảng thời gian đã chọn. */
  analytics: DashboardPart<DashboardSalesAnalytics>;
}

export interface DashboardInventorySnapshot {
  locationId: string;
  overviewSummary: DashboardPart<InventoryDashboardSummaryResponse>;
  balances: DashboardPart<{
    items: InventoryBalanceViewResponse[];
    loaded: number;
    total: number;
  }>;
  transactions: DashboardPart<InventoryTransactionResponse[]>;
}

export interface DashboardAlertSnapshot {
  /** Chỉ có khi bật mock dashboard (service FE in-memory); production `data: null`. */
  stockAlerts: DashboardPart<StockAlert[]>;
  /** `/inventory/dashboards/expiring-batches` (REAL), mock khi API lỗi và bật mock. */
  expiringBatches: DashboardPart<QualityBatchSummary[]>;
  /** Phiếu nhập chờ phê duyệt từ API thật. */
  pendingDocuments: DashboardPart<DashboardPendingDocument[]>;
  /** Chỉ có khi bật mock dashboard; null = GAP. */
  pendingDeductions: DashboardPendingDeduction[] | null;
  expiringReservations: DashboardExpiringReservation[] | null;
}

/** Tổng hợp lệnh sản xuất trong kỳ từ `/inventory/reports/production-wastage`. */
export interface DashboardProductionSummary {
  completedOrderCount: number;
  totalOutputQuantity: number;
  openOrderCount: number;
  shortageOrderCount: number;
  shortageMaterialCount: number;
}

export interface DashboardProductionSnapshot {
  /** null = GAP (API lỗi và mock tắt); mảng rỗng = có API nhưng kỳ này chưa xuất nguyên liệu. */
  wastage: DashboardProductionWastage[] | null;
  summary: DashboardProductionSummary | null;
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

export interface DashboardCategoryBreakdown {
  category: string;
  skuCount: number;
  inventoryValue: number;
  totalQuantity: number;
  percentage: number;
  source: "ADAPTED" | "MOCK";
}

export interface DashboardSlowMovingSku {
  id: string;
  skuCode: string;
  skuName: string;
  category: string;
  locationName: string;
  quantityOnHand: number;
  inventoryValue: number;
  daysUnmoved: number;
  unitSymbol?: string;
  source: "REAL" | "MOCK" | "GAP";
}

export interface DashboardCountAuditSummary {
  id: string;
  countCode: string;
  title: string;
  warehouseName: string;
  status: "DRAFT" | "COUNTING" | "PENDING_APPROVAL" | "COMPLETED" | "CANCELLED";
  countType: string;
  varianceItemsCount: number;
  totalVarianceValue: number;
  createdAt: string;
  source: "ADAPTED" | "MOCK";
}

