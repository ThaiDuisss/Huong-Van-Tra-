import { ApiError } from "@/lib/api-client";

import { formatLocalDate } from "../../../components/shared/TimeHierarchyFilter.utils";
import { inventoryBalanceApi } from "../../inventory/services/inventoryBalance.api";
import { inventoryDashboardApi } from "../../inventory/services/inventoryDashboard.api";
import { inventoryLocationApi } from "../../inventory/services/inventoryLocation.api";
import { inventoryReportApi } from "../../inventory/services/inventoryReport.api";
import { inventoryTransactionApi } from "../../inventory/services/inventoryTransaction.api";
import { qualityApi } from "../../inventory/services/quality.api";
import { stockAlertApi } from "../../inventory/services/stockAlert.api";
import { stockDocumentApi } from "../../inventory/services/stockDocument.api";
import type { QualityBatchQuery, QualityBatchSummary } from "../../inventory/types/quality.types";
import type { StockAlertListQuery } from "../../inventory/types/stock-alert.types";
import type { StockDocumentListQuery } from "../../inventory/types/stock-document.types";
import { getOrderStats } from "../../order/services/order.api";
import { getTodaySales, getTodaySalesSummary } from "../../order/services/sales-history.api";
import {
  mockInventoryBalances,
  mockInventoryOverviewSummary,
  mockInventoryTransactions,
  orderTrend,
  paymentBreakdown,
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
  DashboardProductionWastage,
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

function withTimeout<T>(loader: () => Promise<T>, timeoutMs = 15000): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new ApiError({ code: "TIMEOUT", message: "Máy chủ phản hồi quá lâu.", status: 408 }));
    }, timeoutMs);

    loader()
      .then((res) => {
        clearTimeout(timer);
        resolve(res);
      })
      .catch((err: unknown) => {
        clearTimeout(timer);
        reject(err);
      });
  });
}

/** Bọc từng nguồn để lỗi của một nguồn không làm hỏng cả nhóm. */
async function part<T>(loader: () => Promise<T>, timeoutMs = 15000): Promise<DashboardPart<T>> {
  try {
    return { data: await withTimeout(loader, timeoutMs) };
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
    if (isDashboardMockEnabled) {
      return {
        balances: {
          data: {
            items: mockInventoryBalances as any,
            loaded: mockInventoryBalances.length,
            total: mockInventoryBalances.length,
          },
        },
        locationId,
        overviewSummary: {
          data: mockInventoryOverviewSummary,
        },
        transactions: {
          data: mockInventoryTransactions as any,
        },
      };
    }

    const [overviewSummary, balances, transactions] = await Promise.all([
      part(() => inventoryDashboardApi.getOverviewSummary(undefined, locationId || undefined)),
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

    const finalOverview = overviewSummary.data
      ? overviewSummary
      : isDashboardMockEnabled
        ? { data: mockInventoryOverviewSummary }
        : overviewSummary;

    const finalBalances =
      balances.data && balances.data.items.length > 0
        ? balances
        : isDashboardMockEnabled
          ? {
              data: {
                items: mockInventoryBalances as any,
                loaded: mockInventoryBalances.length,
                total: mockInventoryBalances.length,
              },
            }
          : balances;

    const finalTransactions = transactions.data
      ? transactions
      : isDashboardMockEnabled
        ? { data: mockInventoryTransactions as any }
        : transactions;

    return { balances: finalBalances, locationId, overviewSummary: finalOverview, transactions: finalTransactions };
  },

  async getBackendOverviewSummary(locationId?: string) {
    return part(() => inventoryDashboardApi.getOverviewSummary(undefined, locationId));
  },

  async getBackendLocationBreakdown() {
    return part(() => inventoryDashboardApi.getLocationBreakdown());
  },

  async getBackendTopSkus(locationId?: string) {
    return part(() => inventoryDashboardApi.getTopSkus(undefined, locationId));
  },

  async getBackendExpiryQuality() {
    return part(() => inventoryDashboardApi.getExpiryQualitySummary());
  },

  async getAlertSnapshot(): Promise<DashboardAlertSnapshot> {
    const [stockAlerts, expiringBatches, pendingDocuments, expiringReservationsRes] = await Promise.all([
      part(async () => (await stockAlertApi.listStockAlerts(stockAlertQuery)).content),
      part(async () => {
        try {
          const realBatches = await inventoryDashboardApi.getExpiringBatches(undefined, undefined, 30, 10);
          if (realBatches && realBatches.length > 0) {
            return realBatches.map((b): QualityBatchSummary => ({
              id: b.id,
              batchCode: b.batchCode,
              skuCode: b.skuCode,
              skuName: b.productName,
              productType: "Sản phẩm",
              supplier: "Nhà cung cấp",
              supplierLot: b.batchCode,
              location: {
                id: b.locationId,
                name: b.locationName,
                code: "LOC",
                isCentral: true,
              },
              onHandQuantity: b.onHandQuantity,
              reservedQuantity: 0,
              blockedQuantity: 0,
              availableQuantity: b.availableQuantity,
              damagedQuantity: 0,
              manufacturingDate: "",
              expiryDate: b.expiryDate,
              status: b.daysRemaining < 0 ? "EXPIRED" : "NEAR_EXPIRY",
              unit: b.unit,
              unitCost: 0,
              estimatedValue: 0,
            }));
          }
          if (isDashboardMockEnabled) {
            return (await qualityApi.listQualityBatches(qualityQuery)).content;
          }
          return [];
        } catch {
          if (isDashboardMockEnabled) {
            return (await qualityApi.listQualityBatches(qualityQuery)).content;
          }
          return [];
        }
      }),
      part(async () => (await stockDocumentApi.listStockDocuments(pendingDocumentQuery)).content),
      part(async () => (await import("../../../api/api-client").then((m) => m.apiClient.get<any[]>("/inventory/reservations/expiring-soon")))),
    ]);

    return {
      expiringBatches,
      expiringReservations: expiringReservationsRes.data ? (expiringReservationsRes.data as any) : null,
      pendingDeductions: null,
      pendingDocuments,
      stockAlerts,
    };
  },

  async getProductionSnapshot(): Promise<DashboardProductionSnapshot> {
    const reportRes = await part(() => inventoryReportApi.getProductionWastage());

    if (reportRes.data?.materials && reportRes.data.materials.length > 0) {
      const wastage: DashboardProductionWastage[] = reportRes.data.materials.map((m, index) => {
        const plannedQuantity = m.plannedQuantity ?? 0;
        const actualQuantity = m.actualQuantity ?? 0;
        const wastageQuantity = m.wastageQuantity ?? (actualQuantity > plannedQuantity ? actualQuantity - plannedQuantity : 0);
        const wastageRate = m.wastageRate ?? (plannedQuantity > 0 ? (wastageQuantity / plannedQuantity) * 100 : 0);
        const thresholdRate = 5.0;

        let status: "NORMAL" | "WATCH" | "OVER_THRESHOLD" = "NORMAL";
        if (wastageRate > thresholdRate) {
          status = "OVER_THRESHOLD";
        } else if (wastageRate >= 3.0) {
          status = "WATCH";
        }

        return {
          actualQuantity,
          id: `wastage-mat-${index}-${m.materialCode}`,
          materialCode: m.materialCode,
          materialName: m.materialName,
          plannedQuantity,
          productionOrderCode: "Báo cáo tổng hợp",
          source: "ADAPTED",
          status,
          thresholdRate,
          wastageQuantity,
          wastageRate: Math.round(wastageRate * 10) / 10,
        };
      });

      return { wastage };
    }

    return { wastage: isDashboardMockEnabled ? productionWastage : null };
  },

  async getLocationOptions() {
    const page = await inventoryLocationApi.lookup("", 0, 50);

    return page.content.map((location) => ({ id: String(location.id), name: location.name }));
  },
};
