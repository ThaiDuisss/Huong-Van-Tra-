import { apiClient } from "../../../api/api-client";
import {
  stocktakeScopeLabels,
  stocktakeStatusFilterOptions,
} from "../constants/stocktake.constants";
import type {
  InventoryBalanceViewResponse,
  InventoryLocationResponse,
  PageResponse,
} from "../types/inventory.types";
import type {
  AdjustmentActionInput,
  AdjustmentCreateInput,
  AdjustmentDocumentSummary,
  AdjustmentListItem,
  AdjustmentListQuery,
  StocktakeActionInput,
  StocktakeCountsInput,
  StocktakeCreateInput,
  StocktakeRecountInput,
  StocktakeDetail,
  StocktakeListQuery,
  StocktakeListResult,
  StocktakeOptions,
  StocktakeCountType,
  StocktakeScopeType,
  StocktakeStatus,
  StocktakeSummary,
} from "../types/stocktake.types";
import { temporaryBranchesFromLocations } from "./branch.provider";
import { inventoryBalanceApi } from "./inventoryBalance.api";
import { inventoryItemApi } from "./inventoryItem.api";
import { inventoryLocationApi } from "./inventoryLocation.api";

type AdjustmentSummaryResponse = {
  id: string;
  documentCode: string;
  documentType: "ADJUSTMENT_IN" | "ADJUSTMENT_OUT";
  status: "DRAFT" | "APPROVED" | "POSTED" | "REJECTED";
  approvedBy: string | null;
  approvedAt: string | null;
  postedAt: string | null;
};

type InventoryTakeApiResponse = {
  id: string;
  countCode: string;
  branchId: string;
  scopeType: StocktakeScopeType;
  countType: StocktakeCountType;
  blindCount: boolean;
  status: StocktakeStatus;
  snapshotAt: string | null;
  countingStartedAt: string | null;
  countingCompletedAt: string | null;
  note: string | null;
  createdAt: string;
  createdBy: string | null;
  locationIds?: string[];
  inventoryItemIds?: string[];
  adjustmentDocument?: AdjustmentSummaryResponse | null;
  assignments?: Array<{
    id: string;
    userId: string;
    role: "COUNTER" | "SUPERVISOR";
    status: "ASSIGNED" | "ACCEPTED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
    locationId: string | null;
  }>;
  lines: InventoryTakeLineApiResponse[];
};

type InventoryTakeLineApiResponse = {
  id: string;
  inventoryItemId: string;
  locationId: string;
  inventoryBatchId: string | null;
  batchCode: string | null;
  expiryDate: string | null;
  snapshotQuantity: number | null;
  movementQuantity: number;
  expectedQuantity: number | null;
  snapshotUnitCost: number | null;
  countedQuantity: number | null;
  varianceQuantity: number | null;
  varianceValue: number;
  goodQuantity: number;
  damagedQuantity: number;
  expiredQuantity: number;
  quarantinedQuantity: number;
  reasonCode: string | null;
  note: string | null;
};

type AdjustmentApiResponse = AdjustmentSummaryResponse & {
  branchId: string;
  referenceType: "STOCK_COUNT" | "MANUAL";
  referenceId: string | null;
  reasonCode: string | null;
  note: string | null;
  createdAt: string;
  createdBy: string | null;
  lines: Array<{
    id: string;
    adjustmentQuantity: number;
    inventoryItemId: string;
    locationId: string | null;
  }>;
};

type Enrichment = {
  branches: ReturnType<typeof temporaryBranchesFromLocations>;
  locations: InventoryLocationResponse[];
  balances: InventoryBalanceViewResponse[];
};

export const stocktakeApi = {
  async listAdjustments(query: AdjustmentListQuery): Promise<PageResponse<AdjustmentListItem>> {
    return apiClient.get<PageResponse<AdjustmentListItem>>("/inventory/adjustments", {
      query: {
        branchId: query.branchId,
        keyword: query.keyword,
        page: query.page,
        size: query.size,
        status: query.status,
        sort: "createdAt,desc",
      },
    });
  },
  async getAdjustment(adjustmentId: string): Promise<AdjustmentListItem> {
    return apiClient.get<AdjustmentListItem>(`/inventory/adjustments/${adjustmentId}`);
  },
  async getStocktakeOptions(branchId?: string): Promise<StocktakeOptions> {
    const [locationsPage, itemsPage, balancesPage] = await Promise.all([
      inventoryLocationApi.list({ branchId, page: 0, size: 500, status: "ACTIVE" }),
      inventoryItemApi.list({ page: 0, size: 500, status: "ACTIVE" }),
      inventoryBalanceApi
        .view({ branchId, page: 0, size: 500 })
        .catch(() => emptyPage<InventoryBalanceViewResponse>()),
    ]);
    const views = new Map(
      balancesPage.content.map((balance) => [String(balance.inventoryItemId), balance]),
    );
    return {
      batches: [],
      branches: temporaryBranchesFromLocations(locationsPage.content),
      items: itemsPage.content.map((item) => {
        const view = views.get(String(item.id));
        return {
          hasBatches: false,
          id: String(item.id),
          itemCode: view?.code ?? view?.productCode ?? String(item.referenceId),
          itemName: view?.name ?? view?.productName ?? `Mặt hàng ${String(item.id).slice(0, 8)}`,
          trackingBatch: false,
          unit: view?.unitSymbol ?? view?.unitName ?? "",
        };
      }),
      locations: locationsPage.content.map((location) => ({
        branchId: String(location.branchId),
        id: String(location.id),
        name: location.name,
      })),
      users: [],
    };
  },

  async listStocktakes(query: StocktakeListQuery): Promise<StocktakeListResult> {
    const branchId = query.branchId === "ALL" ? undefined : query.branchId;
    const statuses = stocktakeStatusFilterOptions.find(
      (option) => option.value === query.status,
    )?.statuses;
    const [response, enrichment] = await Promise.all([
      apiClient.get<PageResponse<InventoryTakeApiResponse>>("/inventory/inventorytakes", {
        query: {
          branchId,
          keyword: query.keyword || undefined,
          page: query.page,
          scopeType: query.scopeType === "ALL" ? undefined : query.scopeType,
          size: query.size,
          sort: query.sort,
          statuses,
        },
      }),
      loadEnrichment(branchId, false),
    ]);
    const content = response.content.map((item) => toSummary(item, enrichment));
    return {
      content,
      first: response.first,
      last: response.last,
      page: response.page,
      size: response.size,
      totalElements: response.totalElements,
      totalPages: response.totalPages,
      summary: {
        adjustmentReady: content.filter(
          (item) => item.status === "COMPLETED" && !item.adjustmentDocument,
        ).length,
        completed: content.filter((item) => item.status === "COMPLETED").length,
        counting: content.filter((item) => item.status === "COUNTING").length,
        draft: content.filter((item) => item.status === "DRAFT").length,
        posted: content.filter((item) => item.adjustmentDocument?.status === "POSTED").length,
      },
    };
  },

  async getStocktake(stocktakeId: string): Promise<StocktakeDetail> {
    const response = await apiClient.get<InventoryTakeApiResponse>(
      `/inventory/inventorytakes/${stocktakeId}`,
    );
    return toDetail(response, await loadEnrichment(response.branchId, true));
  },

  async createStocktake(input: StocktakeCreateInput): Promise<StocktakeDetail> {
    const response = await apiClient.post<InventoryTakeApiResponse, StocktakeCreateInput>(
      "/inventory/inventorytakes",
      input,
    );
    return toDetail(response, await loadEnrichment(response.branchId, true));
  },

  async startStocktake(input: StocktakeActionInput) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/start`);
    return this.getStocktake(input.stocktakeId);
  },
  async freezeStocktake(input: StocktakeActionInput) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/freeze`);
    return this.getStocktake(input.stocktakeId);
  },
  async saveStocktakeCount(input: StocktakeCountsInput) {
    await apiClient.put(`/inventory/inventorytakes/${input.stocktakeId}/counts`, {
      lines: input.lines,
    });
    return this.getStocktake(input.stocktakeId);
  },
  async finishStocktake(input: StocktakeActionInput) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/finish`);
    return this.getStocktake(input.stocktakeId);
  },
  async submitStocktakeApproval(input: StocktakeActionInput) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/submit-approval`);
    return this.getStocktake(input.stocktakeId);
  },
  async requestStocktakeRecount(input: StocktakeRecountInput) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/recounts`, {
      assignedUserId: input.assignedUserId,
      lineIds: input.lineIds,
      reason: input.reason,
    });
    return this.getStocktake(input.stocktakeId);
  },
  async approveStocktake(input: StocktakeActionInput) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/approve`);
    return this.getStocktake(input.stocktakeId);
  },
  async returnStocktake(input: StocktakeActionInput & { reason: string }) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/return`, {
      reason: input.reason,
    });
    return this.getStocktake(input.stocktakeId);
  },
  async reopenStocktake(input: StocktakeActionInput) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/reopen`);
    return this.getStocktake(input.stocktakeId);
  },
  async rejectStocktake(input: StocktakeActionInput & { reason: string }) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/reject`, {
      reason: input.reason,
    });
    return this.getStocktake(input.stocktakeId);
  },
  async cancelStocktake(input: StocktakeActionInput & { reason: string }) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/cancel`, {
      reason: input.reason,
    });
    return this.getStocktake(input.stocktakeId);
  },
  async closeStocktake(input: StocktakeActionInput) {
    await apiClient.post(`/inventory/inventorytakes/${input.stocktakeId}/close`);
    return this.getStocktake(input.stocktakeId);
  },
  async createAdjustmentFromStocktake(input: AdjustmentCreateInput) {
    await apiClient.post<AdjustmentApiResponse, AdjustmentCreateInput>(
      "/inventory/adjustments",
      input,
    );
    return this.getStocktake(input.inventoryTakeId);
  },
  async approveStocktakeAdjustment(input: AdjustmentActionInput) {
    await apiClient.post(`/inventory/adjustments/${input.adjustmentId}/approve`);
    return this.getStocktake(input.stocktakeId);
  },
  async postStocktakeAdjustment(input: AdjustmentActionInput) {
    await apiClient.post(`/inventory/adjustments/${input.adjustmentId}/post`);
    return this.getStocktake(input.stocktakeId);
  },
  async rejectStocktakeAdjustment(input: AdjustmentActionInput) {
    await apiClient.post(`/inventory/adjustments/${input.adjustmentId}/reject`);
    return this.getStocktake(input.stocktakeId);
  },
};

async function loadEnrichment(branchId?: string, includeBalances = true): Promise<Enrichment> {
  const [locationsPage, balances] = await Promise.all([
    inventoryLocationApi.list({ branchId, page: 0, size: 500 }),
    includeBalances
      ? inventoryBalanceApi.view({ branchId, page: 0, size: 500 }).then((page) => page.content)
      : Promise.resolve([]),
  ]);
  return {
    balances,
    branches: temporaryBranchesFromLocations(locationsPage.content),
    locations: locationsPage.content,
  };
}

function toSummary(response: InventoryTakeApiResponse, enrichment: Enrichment): StocktakeSummary {
  const adjustmentDocument = toAdjustmentSummary(response.adjustmentDocument ?? null, response.id);
  return {
    adjustmentDocument,
    approvedAt: response.adjustmentDocument?.approvedAt ?? null,
    approvedBy: response.adjustmentDocument?.approvedBy
      ? {
          id: response.adjustmentDocument.approvedBy,
          name: response.adjustmentDocument.approvedBy,
          role: "INVENTORY_STAFF",
        }
      : null,
    branch: enrichment.branches.find((branch) => branch.id === response.branchId) ?? {
      branchCode: "",
      code: "",
      id: response.branchId,
      isCentral: false,
      name: `Chi nhánh ${response.branchId.slice(0, 8)}`,
    },
    countCode: response.countCode,
    countedLines: response.lines.filter((line) => line.countedQuantity !== null).length,
    countingCompletedAt: response.countingCompletedAt,
    countingStartedAt: response.countingStartedAt,
    createdBy: {
      id: response.createdBy ?? "",
      name: response.createdBy ?? "-",
      role: "INVENTORY_STAFF",
    },
    id: response.id,
    note: response.note,
    blindCount: response.blindCount,
    countType: response.countType,
    scopeLabel: stocktakeScopeLabels[response.scopeType],
    scopeType: response.scopeType,
    snapshotAt: response.snapshotAt,
    status: response.status,
    totalLines: response.lines.length,
    varianceLines: response.lines.filter((line) => (line.varianceQuantity ?? 0) !== 0).length,
  };
}

function toDetail(response: InventoryTakeApiResponse, enrichment: Enrichment): StocktakeDetail {
  const locationMap = new Map(
    enrichment.locations.map((location) => [String(location.id), location]),
  );
  const balanceMap = new Map(
    enrichment.balances.map((balance) => [String(balance.inventoryItemId), balance]),
  );
  return {
    ...toSummary(response, enrichment),
    assignments: response.assignments ?? [],
    auditEvents: [],
    inventoryItemIds: response.inventoryItemIds ?? [],
    locationIds: response.locationIds ?? [],
    lines: response.lines.map((line) => {
      const view = balanceMap.get(line.inventoryItemId);
      return {
        batchCode: line.batchCode,
        countedQuantity: line.countedQuantity,
        expiryDate: line.expiryDate,
        id: line.id,
        inventoryBatchId: line.inventoryBatchId,
        inventoryItemCode: view?.code ?? view?.productCode ?? line.inventoryItemId.slice(0, 8),
        inventoryItemId: line.inventoryItemId,
        inventoryItemName: view?.name ?? view?.productName ?? "Mặt hàng tồn kho",
        locationId: line.locationId,
        locationName: locationMap.get(line.locationId)?.name ?? line.locationId.slice(0, 8),
        note: line.note,
        reasonCode: line.reasonCode,
        expectedQuantity: line.expectedQuantity ?? 0,
        movementQuantity: line.movementQuantity,
        snapshotUnitCost: line.snapshotUnitCost ?? 0,
        varianceValue: line.varianceValue,
        goodQuantity: line.goodQuantity,
        damagedQuantity: line.damagedQuantity,
        expiredQuantity: line.expiredQuantity,
        quarantinedQuantity: line.quarantinedQuantity,
        snapshotQuantity: line.snapshotQuantity ?? 0,
        unit: view?.unitSymbol ?? view?.unitName ?? "",
        varianceQuantity: line.varianceQuantity,
      };
    }),
  };
}

function toAdjustmentSummary(
  response: AdjustmentSummaryResponse | null,
  stocktakeId: string,
): AdjustmentDocumentSummary | null {
  if (!response) return null;
  return {
    approvedAt: response.approvedAt,
    approvedBy: response.approvedBy
      ? { id: response.approvedBy, name: response.approvedBy, role: "INVENTORY_STAFF" }
      : null,
    documentCode: response.documentCode,
    documentType: response.documentType,
    id: response.id,
    postedAt: response.postedAt,
    referenceId: stocktakeId,
    referenceType: "STOCK_COUNT",
    status: response.status,
  };
}

function emptyPage<T>(): PageResponse<T> {
  return {
    content: [],
    first: true,
    last: true,
    page: 0,
    size: 0,
    totalElements: 0,
    totalPages: 0,
  };
}
