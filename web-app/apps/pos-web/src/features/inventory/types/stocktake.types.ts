import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    UserSnapshot
} from "./stock-document.types";

export type StocktakeStatus =
    | "DRAFT"
    | "IN_PROGRESS"
    | "COUNTED"
    | "RECONCILING"
    | "PENDING_APPROVAL"
    | "APPROVED"
    | "POSTED"
    | "CANCELLED";

export type StocktakeRole =
    | "WAREHOUSE"
    | "BRANCH_MANAGER"
    | "ADMIN"
    | "DIRECTOR"
    | "ACCOUNTANT"
    | "SALES_STAFF";

export type StocktakeType =
    | "FULL"
    | "CYCLE_COUNT"
    | "CATEGORY"
    | "SKU_SELECTED"
    | "BATCH_SELECTED";

export type StocktakeCondition = "GOOD" | "DAMAGED" | "EXPIRED" | "UNKNOWN";

export type StocktakeReasonCode =
    | "ADJ-01"
    | "ADJ-02"
    | "ADJ-03"
    | "ADJ-04"
    | "ADJ-05"
    | "ADJ-06"
    | "ADJ-07"
    | "ADJ-08"
    | "ADJ-09"
    | "ADJ-99";

export type StocktakeVarianceLevel = "SMALL" | "MEDIUM" | "LARGE";

export interface StocktakeSkuOption {
    id: string;
    skuCode: string;
    name: string;
    category: string;
    batchCode: string;
    expiryDate: string;
    bin: string;
    unit: string;
    systemQuantity: number;
    unitCost: number;
}

export interface StocktakeCountRound {
    round: 1 | 2;
    countedQuantity?: number;
    condition: StocktakeCondition;
    countedBy?: UserSnapshot;
    countedAt?: string;
    note?: string;
}

export interface StocktakeLine {
    id: string;
    skuId: string;
    skuCode: string;
    skuName: string;
    category: string;
    batchCode: string;
    expiryDate: string;
    bin: string;
    unit: string;
    snapshotQuantity: number;
    movementAfterSnapshot: number;
    expectedCurrentQuantity: number;
    finalPhysicalQuantity?: number;
    varianceQuantity: number;
    variancePercent: number;
    financialImpact: number;
    reasonCode?: StocktakeReasonCode;
    reasonNote?: string;
    issueFlag: boolean;
    rounds: StocktakeCountRound[];
    adjustmentSelected: boolean;
}

export interface StocktakeSummary {
    id: string;
    code: string;
    location: InventoryLocationSummary;
    scopeLabel: string;
    status: StocktakeStatus;
    type: StocktakeType;
    totalSku: number;
    countedSku: number;
    hasVariance: boolean;
    varianceValue: number;
    scheduledStartAt: string;
    startedAt?: string;
    owner: UserSnapshot;
    varianceLevel: StocktakeVarianceLevel;
}

export interface StocktakeDetail extends StocktakeSummary {
    blindCount: boolean;
    scope: {
        categoryIds: string[];
        skuIds: string[];
        batchCodes: string[];
    };
    counterRound1: UserSnapshot;
    counterRound2?: UserSnapshot;
    note?: string;
    snapshotFrozenAt?: string;
    adjustmentRequest?: {
        id: string;
        createdBy: UserSnapshot;
        reason: string;
        evidence?: string;
        note?: string;
        increaseValue: number;
        decreaseValue: number;
        status: "DRAFT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "POSTED";
        rejectedReason?: string;
    };
    adjustmentInDocumentId?: string;
    adjustmentInDocumentCode?: string;
    adjustmentOutDocumentId?: string;
    adjustmentOutDocumentCode?: string;
    lines: StocktakeLine[];
    auditEvents: InventoryAuditEvent[];
}

export interface StocktakeListQuery {
    page: number;
    size: number;
    code: string;
    locationId: "ALL" | string;
    status: "ALL" | StocktakeStatus;
    type: "ALL" | StocktakeType;
    ownerId: "ALL" | string;
    startedFrom: string;
    startedTo: string;
    hasVariance: "ALL" | "YES" | "NO";
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
        inProgress: number;
        waitingReconcile: number;
        pendingApproval: number;
        largeVariance: number;
        postedThisMonth: number;
    };
}

export interface StocktakeOptions {
    locations: InventoryLocationSummary[];
    users: UserSnapshot[];
    skus: StocktakeSkuOption[];
    categories: Array<{ id: string; name: string }>;
}

export interface StocktakeCreateInput {
    actorRole: StocktakeRole;
    locationId: string;
    type: StocktakeType;
    blindCount: boolean;
    categoryIds: string[];
    skuIds: string[];
    batchCodes: string[];
    counterRound1Id: string;
    counterRound2Id?: string;
    scheduledStartAt: string;
    note?: string;
}

export interface StocktakeActionInput {
    actorRole: StocktakeRole;
    stocktakeId: string;
    note?: string;
}

export interface StocktakeCountInput extends StocktakeActionInput {
    lineId: string;
    round: 1 | 2;
    countedQuantity: number;
    condition: StocktakeCondition;
    note?: string;
}

export interface StocktakeFinalizeInput extends StocktakeActionInput {
    lines: Array<{
        lineId: string;
        finalPhysicalQuantity: number;
        reasonCode: StocktakeReasonCode;
        reasonNote?: string;
    }>;
}

export interface StocktakeAdjustmentInput extends StocktakeActionInput {
    lineIds: string[];
    reason: string;
    evidence?: string;
    note?: string;
}

export interface StocktakeRejectInput extends StocktakeActionInput {
    reason: string;
}

export interface StocktakeApi {
    listStocktakes(query: StocktakeListQuery): Promise<StocktakeListResult>;
    getStocktake(stocktakeId: string): Promise<StocktakeDetail>;
    createStocktake(input: StocktakeCreateInput): Promise<StocktakeDetail>;
    startStocktake(input: StocktakeActionInput): Promise<StocktakeDetail>;
    saveStocktakeCount(input: StocktakeCountInput): Promise<StocktakeDetail>;
    submitStocktakeRound(input: StocktakeActionInput & { round: 1 | 2 }): Promise<StocktakeDetail>;
    finalizeStocktakeCount(input: StocktakeFinalizeInput): Promise<StocktakeDetail>;
    startStocktakeReconciliation(input: StocktakeActionInput): Promise<StocktakeDetail>;
    createAdjustmentFromStocktake(input: StocktakeAdjustmentInput): Promise<StocktakeDetail>;
    approveStocktakeAdjustment(input: StocktakeActionInput): Promise<StocktakeDetail>;
    rejectStocktakeAdjustment(input: StocktakeRejectInput): Promise<StocktakeDetail>;
    postStocktakeAdjustment(input: StocktakeActionInput): Promise<StocktakeDetail>;
    getStocktakeOptions(): Promise<StocktakeOptions>;
}
