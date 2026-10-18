import { apiClient } from "../../../api/api-client";

export interface InventoryDashboardSummaryResponse {
  skuCount: number;
  totalOnHand: number;
  totalAvailable: number;
  totalReserved: number;
  totalBlocked: number;
  totalInventoryValue?: number;
  belowReorderCount: number;
  pendingDocumentsCount: number;
}

export interface InventoryLocationSummaryResponse {
  locationId: string;
  locationCode: string;
  locationName: string;
  totalOnHand: number;
  totalAvailable: number;
  totalReserved: number;
}

export interface InventoryTopSkuResponse {
  inventoryItemId: string;
  skuCode: string;
  productName: string;
  locationName: string;
  quantityOnHand: number;
  availableQuantity: number;
  unitSymbol: string;
}

export interface InventoryExpiryQualitySummaryResponse {
  expiry7Days: number;
  expiry30Days: number;
  expiredUnhandled: number;
  quarantineOverSla: number;
  damagedValue: number;
  pendingDisposalApprovals: number;
}

export interface InventoryExpiringBatchResponse {
  id: string;
  batchCode: string;
  inventoryItemId: string;
  skuCode: string;
  productName: string;
  locationId: string;
  locationName: string;
  onHandQuantity: number;
  availableQuantity: number;
  unit: string;
  expiryDate: string;
  daysRemaining: number;
  urgency: "CRITICAL" | "HIGH" | "MEDIUM";
  urgencyLabel: string;
  status: string;
}

export const inventoryDashboardApi = {
  getOverviewSummary(branchId?: string, locationId?: string) {
    return apiClient.get<InventoryDashboardSummaryResponse>("/inventory/dashboards/overview", {
      query: { branchId, locationId },
    });
  },

  getLocationBreakdown(branchId?: string) {
    return apiClient.get<InventoryLocationSummaryResponse[]>("/inventory/dashboards/location-breakdown", {
      query: { branchId },
    });
  },

  getTopSkus(branchId?: string, locationId?: string, limit = 6) {
    return apiClient.get<InventoryTopSkuResponse[]>("/inventory/dashboards/top-skus", {
      query: { branchId, locationId, limit },
    });
  },

  getExpiryQualitySummary(branchId?: string, locationId?: string) {
    return apiClient.get<InventoryExpiryQualitySummaryResponse>("/inventory/dashboards/expiry-quality", {
      query: { branchId, locationId },
    });
  },

  getExpiringBatches(branchId?: string, locationId?: string, daysThreshold = 30, limit = 5) {
    return apiClient.get<InventoryExpiringBatchResponse[]>("/inventory/dashboards/expiring-batches", {
      query: { branchId, locationId, daysThreshold, limit },
    });
  },
};
