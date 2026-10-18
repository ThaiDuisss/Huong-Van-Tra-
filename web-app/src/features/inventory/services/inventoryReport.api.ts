import { apiClient } from "../../../api/api-client";

export interface InventoryValueReportResponse {
  totalValue: number;
  totalOnHandQuantity: number;
  totalSkuCount: number;
  categoryBreakdown: Array<{
    categoryCode: string;
    categoryName: string;
    skuCount: number;
    totalQuantity: number;
    totalValue: number;
    percentage: number;
  }>;
  locationBreakdown: Array<{
    locationId: string;
    locationCode: string;
    locationName: string;
    totalQuantity: number;
    totalValue: number;
  }>;
  topValuedItems: Array<{
    inventoryItemId: string;
    code: string;
    name: string;
    categoryName: string;
    locationName: string;
    unitSymbol: string;
    quantityOnHand: number;
    costPrice: number;
    totalValue: number;
  }>;
}

export interface InventoryTurnoverReportResponse {
  periodDays: number;
  averageTurnoverRate: number;
  averageDsi: number;
  slowMovingSkuCount: number;
  items: Array<{
    inventoryItemId: string;
    code: string;
    name: string;
    categoryName: string;
    unitSymbol: string;
    averageStock: number;
    totalSoldQuantity: number;
    totalCogs: number;
    turnoverRate: number;
    dsi: number;
    abcCategory: "A" | "B" | "C";
  }>;
}

export interface ProductionWastageReportResponse {
  totalPlannedQuantity: number;
  totalActualQuantity: number;
  totalWastageQuantity: number;
  totalWastageValue: number;
  materials: Array<{
    materialCode: string;
    materialName: string;
    unitSymbol: string;
    plannedQuantity: number;
    actualQuantity: number;
    wastageQuantity: number;
    wastageRate: number;
    estimatedLossValue: number;
  }>;
}

export interface DisposalSummaryReportResponse {
  totalDisposalValue: number;
  expiredBatchesCount: number;
  damagedBatchesCount: number;
  customerReturnsCount: number;
  supplierReturnsCount: number;
  writeOffDocumentsCount: number;
  items: Array<{
    code: string;
    name: string;
    type: string;
    sourceDocumentCode: string;
    quantity: number;
    unitSymbol: string;
    estimatedValue: number;
    reason: string;
    date: string;
  }>;
}

export interface InTransitSummaryResponse {
  totalInTransitShipments: number;
  totalInTransitQuantity: number;
  totalInTransitValue: number;
  shipments: Array<{
    documentId: string;
    documentCode: string;
    sourceLocationName: string;
    destinationLocationName: string;
    totalQuantity: number;
    estimatedValue: number;
    createdAt: string;
  }>;
}

export const inventoryReportApi = {
  getInventoryValue(params?: { fromDate?: string; toDate?: string; locationId?: string; categoryCode?: string }) {
    return apiClient.get<InventoryValueReportResponse>("/inventory/reports/inventory-value", { query: params });
  },

  getTurnover(params?: { fromDate?: string; toDate?: string; locationId?: string; categoryCode?: string }) {
    return apiClient.get<InventoryTurnoverReportResponse>("/inventory/reports/turnover", { query: params });
  },

  getProductionWastage(params?: { fromDate?: string; toDate?: string }) {
    return apiClient.get<ProductionWastageReportResponse>("/inventory/reports/production-wastage", { query: params });
  },

  getDisposalSummary(params?: { fromDate?: string; toDate?: string; locationId?: string }) {
    return apiClient.get<DisposalSummaryReportResponse>("/inventory/reports/disposal-summary", { query: params });
  },

  getInTransitSummary() {
    return apiClient.get<InTransitSummaryResponse>("/inventory/reports/in-transit-summary");
  },
};
