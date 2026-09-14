import { ApiError } from "@hvt/api-client";

import { formatLocalDate } from "../../../components/shared/TimeHierarchyFilter.utils";
import { inventoryBalanceApi } from "../../inventory/services/inventoryBalance.api";
import { inventoryLocationApi } from "../../inventory/services/inventoryLocation.api";
import { inventoryTransactionApi } from "../../inventory/services/inventoryTransaction.api";
import { qualityApi } from "../../inventory/services/quality.api";
import { stockAlertApi } from "../../inventory/services/stockAlert.api";
import { stockDocumentApi } from "../../inventory/services/stockDocument.api";
import type { QualityBatchQuery } from "../../inventory/types/quality.types";
import type { StockAlertListQuery } from "../../inventory/types/stock-alert.types";
import type { StockDocumentListQuery } from "../../inventory/types/stock-document.types";
import { getOrderStats } from "../../order/services/order.api";
import { getTodaySales, getTodaySalesSummary } from "../../order/services/sales-history.api";
import {
  expiringReservations,
  orderTrend,
  paymentBreakdown,
  pendingDeductions,
  productionWastage,
  revenueTrend,
  staffPerformance,
  storePerformance,
  topSkusByRevenue,
} from "../mocks/dashboard.mock";
import type {
  DashboardAlertSnapshot,
  DashboardInventorySnapshot,
  DashboardPart,
  DashboardProductionSnapshot,
  DashboardSalesSnapshot,
  DashboardTimeRange,
} from "../types/dashboard.types";
import {
  DASHBOARD_BALANCE_PAGE_SIZE,
  DASHBOARD_LIST_SIZE,
  isDashboardMockEnabled,
} from "./dashboard.config";

const qualityQuery: QualityBatchQuery = {
  batchCode: "",
  expiryFrom: "",
  expiryTo: "",
  locationId: "ALL",
  minValue: 0,
  productType: "ALL",
  quarantineDays: 0,
  reason: "",
  sku: "",
  status: "ALL",
  supplier: "",
  tab: "NEAR_EXPIRY",
};

const stockAlertQuery: StockAlertListQuery = {
  createdFrom: "",
  hasAction: "ALL",
  locationId: "ALL",
  page: 0,
  role: "ADMIN",
  severity: "ALL",
  size: 20,
  skuOrCategory: "",
  status: "OPEN",
  type: "ALL",
};

const pendingDocumentQuery: StockDocumentListQuery = {
  createdById: "ALL",
  createdFrom: "",
  createdTo: "",
  destinationLocationId: "ALL",
  direction: "ALL",
  documentCode: "",
  hasBatch: "ALL",
  locationId: "ALL",
  page: 0,
  postedFrom: "",
  postedTo: "",
  referenceCode: "",
  size: 20,
  sourceLocationId: "ALL",
  status: "SUBMITTED",
  type: "ALL",
};

/** Thông điệp thân thiện theo loại lỗi; không lộ raw server error ra UI. */
export function describeDashboardError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 0) return "Không kết nối được máy chủ.";
    if (error.status === 408) return "Máy chủ phản hồi quá lâu.";
    if (error.status === 401 || error.status === 403) return "Bạn không có quyền xem dữ liệu này.";
    if (error.status === 404) return "API chưa khả dụng trên môi trường này.";
    if (error.status >= 500) return "Máy chủ gặp lỗi, vui lòng thử lại.";
  }

  return "Không thể tải dữ liệu.";
}

/** Bọc từng nguồn để lỗi của một nguồn không làm hỏng cả nhóm. */
async function part<T>(loader: () => Promise<T>): Promise<DashboardPart<T>> {
  try {
    return { data: await loader() };
  } catch (error) {
    return { data: null, error: describeDashboardError(error) };
  }
}

export function toDateRange(range: DashboardTimeRange) {
  const end = new Date();
  const start = new Date(end);

  if (range === "SEVEN_DAYS") {
    start.setDate(end.getDate() - 6);
  }

  if (range === "THIRTY_DAYS") {
    start.setDate(end.getDate() - 29);
  }

  return {
    fromDate: formatLocalDate(start),
    toDate: formatLocalDate(end),
  };
}

export const dashboardApi = {
  async getSalesSnapshot(timeRange: DashboardTimeRange): Promise<DashboardSalesSnapshot> {
    const dateRange = toDateRange(timeRange);
    const [todaySummary, stats, recentOrders] = await Promise.all([
      part(() => getTodaySalesSummary()),
      part(() => getOrderStats(dateRange)),
      part(async () => {
        const page = await getTodaySales({
          filters: { paymentMethod: "ALL", status: "ALL" },
          page: 0,
          size: DASHBOARD_LIST_SIZE,
        });

        return { items: page.content, total: page.totalElements };
      }),
    ]);

    return {
      ...dateRange,
      demo: isDashboardMockEnabled
        ? {
            orderTrend,
            paymentBreakdown,
            revenueTrend,
            staffPerformance,
            storePerformance,
            topSkus: topSkusByRevenue,
          }
        : null,
      recentOrders,
      stats,
      timeRange,
      todaySummary,
    };
  },

  async getInventorySnapshot(locationId: string): Promise<DashboardInventorySnapshot> {
    const [balances, transactions] = await Promise.all([
      part(async () => {
        const page = await inventoryBalanceApi.view({
          locationId: locationId || undefined,
          page: 0,
          size: DASHBOARD_BALANCE_PAGE_SIZE,
        });

        return {
          items: page.content,
          loaded: page.content.length,
          total: page.totalElements,
        };
      }),
      part(async () => {
        const page = await inventoryTransactionApi.list({ page: 0, size: DASHBOARD_LIST_SIZE });

        return page.content;
      }),
    ]);

    return { balances, locationId, transactions };
  },

  async getAlertSnapshot(): Promise<DashboardAlertSnapshot> {
    const [stockAlerts, expiringBatches, pendingDocuments] = await Promise.all([
      part(async () => (await stockAlertApi.listStockAlerts(stockAlertQuery)).content),
      part(async () => (await qualityApi.listQualityBatches(qualityQuery)).content),
      part(async () => (await stockDocumentApi.listStockDocuments(pendingDocumentQuery)).content),
    ]);

    return {
      expiringBatches,
      expiringReservations: isDashboardMockEnabled ? expiringReservations : null,
      pendingDeductions: isDashboardMockEnabled ? pendingDeductions : null,
      pendingDocuments,
      stockAlerts,
    };
  },

  async getProductionSnapshot(): Promise<DashboardProductionSnapshot> {
    // TODO(BE): thay bằng API hao hụt sản xuất khi có (DASH-GAP-013).
    return { wastage: isDashboardMockEnabled ? productionWastage : null };
  },

  async getLocationOptions() {
    const page = await inventoryLocationApi.lookup("", 0, 50);

    return page.content.map((location) => ({ id: String(location.id), name: location.name }));
  },
};
