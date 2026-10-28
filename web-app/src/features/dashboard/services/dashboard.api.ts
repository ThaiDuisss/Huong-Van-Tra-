import { ApiError } from "@/lib/api-client";

import { apiClient } from "../../../api/api-client";

import { formatLocalDate } from "../../../components/shared/TimeHierarchyFilter.utils";
import { inventoryBalanceApi } from "../../inventory/services/inventoryBalance.api";
import { inventoryDashboardApi } from "../../inventory/services/inventoryDashboard.api";
import { inventoryLocationApi } from "../../inventory/services/inventoryLocation.api";
import {
  inventoryReportApi,
  type ProductionWastageReportResponse,
} from "../../inventory/services/inventoryReport.api";
import { inventoryTransactionApi } from "../../inventory/services/inventoryTransaction.api";
import { qualityApi } from "../../inventory/services/quality.api";
import { stockAlertApi } from "../../inventory/services/stockAlert.api";
import type { QualityBatchQuery, QualityBatchSummary } from "../../inventory/types/quality.types";
import type { StockAlertListQuery } from "../../inventory/types/stock-alert.types";
import type { PageResponse } from "../../inventory/types/inventory.types";
import { getOrderStats } from "../../order/services/order.api";
import { getSalesDashboard } from "../../order/services/sales-dashboard.api";
import { getTodaySales, getTodaySalesSummary } from "../../order/services/sales-history.api";
import type { SalesDashboardResponse } from "../../order/types/sales-dashboard.types";
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
  DashboardPendingDocument,
  DashboardProductionSnapshot,
  DashboardProductionWastage,
  DashboardSalesAnalytics,
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
  role: "SUPER_ADMIN",
  severity: "ALL",
  size: 20,
  skuOrCategory: "",
  status: "OPEN",
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

function toNumber(value: number | string | null | undefined) {
  const parsed = typeof value === "string" ? Number(value) : (value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

const paymentMethodLabels: Record<string, string> = {
  BANK_TRANSFER: "Chuyển khoản",
  CASH: "Tiền mặt",
  DEBT: "Ghi nợ",
};

/** Chuyển response backend sang cấu trúc widget; nguồn REAL. */
export function toSalesAnalytics(response: SalesDashboardResponse): DashboardSalesAnalytics {
  return {
    orderTrend: response.hourlyTrend.map((point) => ({
      label: point.label,
      source: "REAL",
      value: point.orderCount,
    })),
    paymentBreakdown: response.paymentBreakdown.map((row) => {
      const method = row.method || "UNKNOWN";
      return {
        label: paymentMethodLabels[method] ?? (row.method || "Chưa ghi nhận"),
        method,
        revenue: toNumber(row.revenue),
        status: "REAL",
        transactionCount: row.orderCount,
      };
    }),
    revenueTrend: response.hourlyTrend.map((point) => ({
      label: point.label,
      secondaryValue: point.orderCount,
      source: "REAL",
      value: toNumber(point.revenue),
    })),
    staffPerformance: response.topStaff.map((staff) => ({
      cancelRefundRate:
        staff.orderCount > 0 ? Math.round((staff.refundedOrderCount / staff.orderCount) * 1000) / 10 : 0,
      id: staff.cashierId ?? `no-cashier-${staff.rank}`,
      orderCount: staff.orderCount,
      revenue: toNumber(staff.revenue),
      source: "REAL",
      staffName: staff.cashierName || "Chưa gán thu ngân",
    })),
    storePerformance: response.storePerformance.map((store, index) => ({
      averageOrderValue: toNumber(store.averageOrderValue),
      id: store.branchId ?? `no-branch-${index}`,
      orderCount: store.orderCount,
      revenue: toNumber(store.revenue),
      source: "REAL",
      storeName: store.branchName || "Chưa gán chi nhánh",
    })),
    topSkus: response.topProducts.map((product) => ({
      category: product.variantName || "",
      id: product.productVariantId,
      quantity: toNumber(product.quantitySold),
      revenue: toNumber(product.revenue),
      skuCode: product.sku ?? "",
      skuName: product.productName || product.sku || "Sản phẩm",
      source: "REAL",
    })),
  };
}

const mockSalesAnalytics: DashboardSalesAnalytics = {
  orderTrend,
  paymentBreakdown,
  revenueTrend,
  staffPerformance,
  storePerformance,
  topSkus: topSkusByRevenue,
};

const pendingDocumentTypeLabel = "Phiếu nhập kho";

/** Các trường dùng từ `GET /inventory/goods-receipts` (GoodsReceiptListItemResponse). */
interface PendingDocumentBackendRow {
  id: string;
  documentCode: string;
  locationName?: string | null;
  supplierName?: string | null;
  createdAt?: string | null;
}

export const dashboardApi = {
  async getSalesSnapshot(timeRange: DashboardTimeRange): Promise<DashboardSalesSnapshot> {
    const dateRange = toDateRange(timeRange);
    const [todaySummary, stats, analytics, recentOrders] = await Promise.all([
      part(() => getTodaySalesSummary()),
      part(() => getOrderStats(dateRange)),
      part(async () => toSalesAnalytics(await getSalesDashboard(dateRange))),
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
      analytics:
        analytics.data === null && isDashboardMockEnabled ? { data: mockSalesAnalytics } : analytics,
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
      // Stock alert chưa có backend: chỉ hiển thị khi bật mock dashboard.
      isDashboardMockEnabled
        ? part(async () => (await stockAlertApi.listStockAlerts(stockAlertQuery)).content)
        : Promise.resolve<DashboardPart<never[]>>({ data: null }),
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
      part(async (): Promise<DashboardPendingDocument[]> => {
        const page = await apiClient.get<PageResponse<PendingDocumentBackendRow>>("/inventory/goods-receipts", {
          query: { page: 0, size: DASHBOARD_LIST_SIZE * 2, status: "PENDING_APPROVAL" },
        });

        return page.content.map((document) => ({
          code: document.documentCode,
          createdAt: document.createdAt ?? null,
          id: document.id,
          locationName: document.locationName ?? null,
          supplierName: document.supplierName ?? null,
          typeLabel: pendingDocumentTypeLabel,
        }));
      }),
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

  async getProductionSnapshot(timeRange: DashboardTimeRange): Promise<DashboardProductionSnapshot> {
    let report: ProductionWastageReportResponse;

    try {
      report = await withTimeout(() => inventoryReportApi.getProductionWastage(toDateRange(timeRange)));
    } catch (error) {
      if (isDashboardMockEnabled) {
        return { summary: null, wastage: productionWastage };
      }

      // Lỗi đẩy lên useDomainResource để tab hiện thông báo theo loại lỗi và nút thử lại.
      throw error;
    }

    const wastage: DashboardProductionWastage[] = report.materials.map((m, index) => ({
      actualQuantity: toNumber(m.actualQuantity),
      id: `wastage-mat-${index}-${m.materialCode ?? m.materialName}`,
      materialCode: m.materialCode ?? "",
      materialName: m.materialName,
      plannedQuantity: toNumber(m.plannedQuantity),
      productionOrderCode: `${m.productionOrderCount} lệnh`,
      source: "REAL",
      status: m.overThreshold ? "OVER_THRESHOLD" : "NORMAL",
      thresholdRate: toNumber(m.thresholdRate),
      wastageQuantity: toNumber(m.wastageQuantity),
      wastageRate: toNumber(m.wastageRate),
    }));

    return {
      summary: {
        completedOrderCount: toNumber(report.completedOrderCount),
        openOrderCount: toNumber(report.openOrderCount),
        shortageMaterialCount: toNumber(report.shortageMaterialCount),
        shortageOrderCount: toNumber(report.shortageOrderCount),
        totalOutputQuantity: toNumber(report.totalOutputQuantity),
      },
      wastage,
    };
  },

  async getLocationOptions() {
    const page = await inventoryLocationApi.lookup("", 0, 50);

    return page.content.map((location) => ({ id: String(location.id), name: location.name }));
  },
};
