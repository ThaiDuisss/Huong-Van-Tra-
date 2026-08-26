import type { InventoryAuditEvent, InventoryLocationSummary, UserSnapshot } from "./stock-document.types";

export type StocktakeScopeType = "FULL" | "LOCATION" | "PARTIAL";
export type StocktakeStatus = "DRAFT" | "COUNTING" | "COMPLETED";
export type AdjustmentStatus = "DRAFT" | "APPROVED" | "POSTED" | "REJECTED";
export type AdjustmentReferenceType = "STOCK_COUNT" | "MANUAL";
export type AdjustmentDocumentType = "ADJUSTMENT_IN" | "ADJUSTMENT_OUT";
export type StocktakeRole = "WAREHOUSE" | "BRANCH_MANAGER" | "ADMIN" | "DIRECTOR" | "ACCOUNTANT" | "SALES_STAFF";

export interface InventoryBatchSummary {
    id: string;
    batchCode: string;
    expiryDate: string | null;
}

export interface StocktakeLine {
    id: string;
    inventoryItemId: string;
    inventoryItemCode: string;
    inventoryItemName: string;
    locationId: string;
    locationName: string;
    inventoryBatchId: string | null;
    batchCode: string | null;
    expiryDate: string | null;
    snapshotQuantity: number;
    countedQuantity: number | null;
    varianceQuantity: number | null;
    reasonCode: string | null;
    note: string | null;
    unit: string;
}

export interface StocktakeSummary {
    id: string;
    countCode: string;
    branch: InventoryLocationSummary;
    scopeType: StocktakeScopeType;
    scopeLabel: string;
    status: StocktakeStatus;
    snapshotAt: string | null;
    countingStartedAt: string | null;
    countingCompletedAt: string | null;
    totalLines: number;
    countedLines: number;
    varianceLines: number;
    createdBy: UserSnapshot;
    approvedBy: UserSnapshot | null;
    approvedAt: string | null;
    note: string | null;
}

export interface StocktakeDetail extends StocktakeSummary {
    approvedBy: UserSnapshot | null;
    approvedAt: string | null;
    adjustmentDocument: AdjustmentDocumentSummary | null;
    lines: StocktakeLine[];
    auditEvents: InventoryAuditEvent[];
}

export interface AdjustmentDocumentSummary {
    id: string;
    documentCode: string;
    documentType: AdjustmentDocumentType;
    status: AdjustmentStatus;
    referenceType: AdjustmentReferenceType;
    referenceId: string | null;
    approvedBy: UserSnapshot | null;
    approvedAt: string | null;
    postedAt: string | null;
}

export interface StocktakeListQuery {
    page: number;
    size: number;
    branchId: "ALL" | string;
    status: "ALL" | StocktakeStatus;
    scopeType: "ALL" | StocktakeScopeType;
}

export interface StocktakeListResult {
    content: StocktakeSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
    summary: {
        draft: number;
        counting: number;
        completed: number;
        adjustmentReady: number;
        posted: number;
    };
}

export interface StocktakeOptions {
    branches: InventoryLocationSummary[];
    locations: Array<{ id: string; name: string; branchId: string }>;
    items: Array<{ id: string; itemCode: string; itemName: string; trackingBatch: boolean; unit: string; hasBatches: boolean }>;
    batches: InventoryBatchSummary[];
    users: UserSnapshot[];
}

export interface StocktakeCreateInput {
    branchId: string;
    scopeType: StocktakeScopeType;
    locationIds?: string[];
    inventoryItemIds?: string[];
    note?: string;
}

export interface StocktakeActionInput {
    stocktakeId: string;
}

export interface StocktakeCountsInput extends StocktakeActionInput {
    lines: Array<{
        lineId: string;
        countedQuantity: number;
        reasonCode?: string;
        note?: string;
    }>;
}

export interface AdjustmentCreateInput {
    branchId: string;
    inventoryTakeId?: string;
    lines?: Array<{
        inventoryItemId: string;
        sourceLocationId: string;
        inventoryBatchId?: string | null;
        requestedQuantity: number;
        note?: string;
    }>;
}

export type AdjustmentActionInput = StocktakeActionInput;
