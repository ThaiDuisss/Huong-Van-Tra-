import { apiClient } from "../../../api/api-client";
import { inventoryLocationApi } from "./inventoryLocation.api";
import type { PageResponse } from "../types/inventory.types";
import type {
    AdjustmentActionInput,
    AdjustmentCreateInput,
    StocktakeActionInput,
    StocktakeCountsInput,
    StocktakeCreateInput,
    StocktakeDetail,
    StocktakeListQuery,
    StocktakeListResult,
    StocktakeOptions,
    StocktakeScopeType,
    StocktakeStatus,
    StocktakeSummary
} from "../types/stocktake.types";

type InventoryTakeApiResponse = {
    id: string;
    countCode: string;
    branchId: string;
    scopeType: StocktakeScopeType;
    status: StocktakeStatus;
    snapshotAt: string | null;
    countingStartedAt: string | null;
    countingCompletedAt: string | null;
    note: string | null;
    createdAt: string;
    createdBy: string;
    lines: Array<{
        id: string;
        inventoryItemId: string;
        locationId: string;
        inventoryBatchId: string | null;
        batchCode: string | null;
        expiryDate: string | null;
        snapshotQuantity: number;
        countedQuantity: number | null;
        varianceQuantity: number | null;
        reasonCode: string | null;
        note: string | null;
    }>;
};

type AdjustmentApiResponse = {
    id: string;
    documentCode: string;
    documentType: "ADJUSTMENT_IN" | "ADJUSTMENT_OUT";
    status: "DRAFT" | "APPROVED" | "POSTED" | "REJECTED";
    branchId: string;
    reasonCode: string | null;
    note: string | null;
    approvedBy: string | null;
    approvedAt: string | null;
    postedAt: string | null;
    createdAt: string;
    createdBy: string;
    lines: Array<{
        id: string;
        lineNo: number;
        inventoryItemId: string;
        locationId: string;
        adjustmentQuantity: number;
        appliedQuantity: number;
        note: string | null;
    }>;
};

export const stocktakeApi = {
    async getStocktakeOptions(): Promise<StocktakeOptions> {
        const [locations] = await Promise.all([
            inventoryLocationApi.list({ page: 0, size: 500 })
        ]);
        return {
            batches: [],
            branches: locations.content.map((location) => ({
                branchCode: "",
                code: location.code,
                id: String(location.id),
                isCentral: location.type === "WAREHOUSE",
                name: location.name
            })),
            items: [],
            locations: locations.content.map((location) => ({
                branchId: String(location.branchId),
                id: String(location.id),
                name: location.name
            })),
            users: []
        };
    },

    async listStocktakes(query: StocktakeListQuery): Promise<StocktakeListResult> {
        const response = await apiClient.get<PageResponse<InventoryTakeApiResponse>>("/inventory/inventorytakes", {
            query: {
                branchId: query.branchId === "ALL" ? undefined : query.branchId,
                page: query.page,
                scopeType: query.scopeType === "ALL" ? undefined : query.scopeType,
                size: query.size,
                status: query.status === "ALL" ? undefined : query.status
            }
        });
        return {
            content: response.content.map(toSummary),
            first: response.first,
            last: response.last,
            page: response.page,
            size: response.size,
            summary: { adjustmentReady: 0, completed: 0, counting: 0, draft: 0, posted: 0 },
            totalElements: response.totalElements,
            totalPages: response.totalPages
        };
    },

    async getStocktake(stocktakeId: string): Promise<StocktakeDetail> {
        const response = await apiClient.get<InventoryTakeApiResponse>(`/inventory/inventorytakes/${stocktakeId}`);
        return toDetail(response);
    },

    async createStocktake(input: StocktakeCreateInput): Promise<StocktakeDetail> {
        const response = await apiClient.post<InventoryTakeApiResponse, StocktakeCreateInput>("/inventory/inventorytakes", input);
        return toDetail(response);
    },

    async startStocktake(input: StocktakeActionInput): Promise<StocktakeDetail> {
        return toDetail(await apiClient.post<InventoryTakeApiResponse>(`/inventory/inventorytakes/${input.stocktakeId}/start`));
    },

    async saveStocktakeCount(input: StocktakeCountsInput): Promise<StocktakeDetail> {
        return toDetail(await apiClient.put<InventoryTakeApiResponse, StocktakeCountsInput>(`/inventory/inventorytakes/${input.stocktakeId}/counts`, input));
    },

    async finishStocktake(input: StocktakeActionInput): Promise<StocktakeDetail> {
        return toDetail(await apiClient.post<InventoryTakeApiResponse>(`/inventory/inventorytakes/${input.stocktakeId}/finish`));
    },

    async createAdjustmentFromStocktake(input: AdjustmentCreateInput): Promise<StocktakeDetail> {
        await apiClient.post<AdjustmentApiResponse, AdjustmentCreateInput>("/inventory/adjustments", input);
        return this.getStocktake(input.inventoryTakeId ?? "");
    },

    async approveStocktakeAdjustment(input: AdjustmentActionInput): Promise<StocktakeDetail> {
        await apiClient.post<AdjustmentApiResponse>(`/inventory/adjustments/${input.stocktakeId}/approve`);
        return this.getStocktake(input.stocktakeId);
    },

    async postStocktakeAdjustment(input: AdjustmentActionInput): Promise<StocktakeDetail> {
        await apiClient.post<AdjustmentApiResponse>(`/inventory/adjustments/${input.stocktakeId}/post`);
        return this.getStocktake(input.stocktakeId);
    },

    async rejectStocktakeAdjustment(input: AdjustmentActionInput & { reason: string }): Promise<StocktakeDetail> {
        await apiClient.post<AdjustmentApiResponse, { reason: string }>(`/inventory/adjustments/${input.stocktakeId}/reject`, { reason: input.reason });
        return this.getStocktake(input.stocktakeId);
    }
};

function toSummary(response: InventoryTakeApiResponse): StocktakeSummary {
    return {
        approvedAt: null,
        approvedBy: null,
        branch: {
            branchCode: "",
            code: "",
            id: response.branchId,
            isCentral: false,
            name: ""
        },
        countCode: response.countCode,
        countedLines: response.lines.filter((line) => line.countedQuantity !== null).length,
        countingCompletedAt: response.countingCompletedAt,
        countingStartedAt: response.countingStartedAt,
        createdBy: { id: response.createdBy, name: "", role: "WAREHOUSE" },
        id: response.id,
        note: response.note,
        scopeLabel: response.scopeType,
        scopeType: response.scopeType,
        snapshotAt: response.snapshotAt,
        status: response.status,
        totalLines: response.lines.length,
        varianceLines: response.lines.filter((line) => (line.varianceQuantity ?? 0) !== 0).length
    };
}

function toDetail(response: InventoryTakeApiResponse): StocktakeDetail {
    const summary = toSummary(response);
    return {
        ...summary,
        adjustmentDocument: null,
        auditEvents: [],
        lines: response.lines.map((line) => ({
            batchCode: line.batchCode,
            countedQuantity: line.countedQuantity,
            expiryDate: line.expiryDate,
            id: line.id,
            inventoryBatchId: line.inventoryBatchId,
            inventoryItemCode: "",
            inventoryItemId: line.inventoryItemId,
            inventoryItemName: "",
            locationId: line.locationId,
            locationName: "",
            note: line.note,
            reasonCode: line.reasonCode,
            snapshotQuantity: line.snapshotQuantity,
            unit: "",
            varianceQuantity: line.varianceQuantity
        }))
    };
}
