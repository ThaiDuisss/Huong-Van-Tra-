import type {
    InventoryLocationSummary,
    UserSnapshot
} from "./stock-document.types";

export type StockAlertType =
    | "BELOW_MINIMUM"
    | "BELOW_REORDER_POINT"
    | "OUT_OF_STOCK"
    | "NEGATIVE_AVAILABLE_TO_PROMISE"
    | "HIGH_RESERVATION"
    | "EXPIRING_SOON"
    | "TRANSFER_RECOMMENDED"
    | "PURCHASE_RECOMMENDED";

export type StockAlertStatus =
    | "OPEN"
    | "ACKNOWLEDGED"
    | "ACTION_CREATED"
    | "RESOLVED"
    | "DISMISSED";

export type StockAlertSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type StockAlertRole = "WAREHOUSE" | "BRANCH_MANAGER" | "ADMIN" | "ACCOUNTANT" | "DIRECTOR" | "SALES_STAFF";
export type SuggestedActionType = "TRANSFER" | "PURCHASE" | "NONE";

export interface StockSkuSummary {
    id: string;
    skuCode: string;
    skuName: string;
    category: string;
    unit: string;
    unitCost: number;
    preferredSupplier?: string;
}

export interface InventoryBalanceSnapshot {
    onHand: number;
    reserved: number;
    blocked: number;
    available: number;
    availableToPromise: number;
    inTransitQuantity: number;
}

export interface ReorderPolicy {
    id: string;
    locationId: string;
    skuId: string;
    minimumStock: number;
    safetyStock: number;
    reorderPoint: number;
    standardReorderQuantity: number;
    maximumStock?: number;
    supplierLeadTimeDays?: number;
    averageDailyConsumption?: number;
    enabled: boolean;
    overrideReason?: string;
}

export interface StockAlert {
    id: string;
    code: string;
    type: StockAlertType;
    severity: StockAlertSeverity;
    status: StockAlertStatus;
    sku: StockSkuSummary;
    location: InventoryLocationSummary;
    balance: InventoryBalanceSnapshot;
    policy: ReorderPolicy;
    suggestedQuantity: number;
    suggestedAction: SuggestedActionType;
    createdAt: string;
    updatedAt: string;
    slaDueAt: string;
    acknowledgedAt?: string;
    acknowledgedBy?: UserSnapshot;
    dismissedAt?: string;
    dismissedReason?: string;
    dismissUntil?: string;
    resolvedAt?: string;
    resolvedReason?: string;
    actionReference?: {
        id: string;
        code: string;
        type: "TRANSFER_REQUEST" | "PURCHASE_REQUEST";
        status: string;
        url?: string;
    };
    calculationExplanation: string;
}

export interface StockAlertDetail extends StockAlert {
    batchBreakdown: Array<{
        batchCode: string;
        onHand: number;
        reserved: number;
        blocked: number;
        expiryDate: string;
        status: string;
    }>;
    consumptionTrend: {
        averageDailyConsumption: number;
        last7Days: number;
        last30Days: number;
        note: string;
    };
    openProductionOrders: Array<{ id: string; code: string; quantity: number; dueAt: string }>;
    openReservations: Array<{ id: string; code: string; quantity: number; expiresAt: string }>;
    existingTransferRequest?: { id: string; code: string; status: string };
    existingPurchaseRequest?: { id: string; code: string; status: string; estimatedValue: number };
    internalSource?: {
        sourceLocation: InventoryLocationSummary;
        sourceAvailableAfterSafetyStock: number;
        suggestedTransferQuantity: number;
        expectedArrival: string;
    };
}

export interface StockAlertListQuery {
    page: number;
    size: number;
    role: StockAlertRole;
    type: "ALL" | StockAlertType;
    severity: "ALL" | StockAlertSeverity;
    locationId: "ALL" | string;
    skuOrCategory: string;
    status: "ALL" | StockAlertStatus;
    createdFrom: string;
    hasAction: "ALL" | "YES" | "NO";
}

export interface StockAlertListResult {
    content: StockAlert[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    summary: {
        outOfStock: number;
        belowReorderPoint: number;
        transferNeeded: number;
        purchaseNeeded: number;
        critical: number;
        overSla: number;
    };
}

export interface ReorderPolicyQuery {
    locationId: "ALL" | string;
    skuOrCategory: string;
    enabled: "ALL" | "YES" | "NO";
}

export interface StockAlertActionInput {
    alertId: string;
    actorRole: StockAlertRole;
    note?: string;
    reason?: string;
    dismissUntil?: string;
    adminOverride?: boolean;
}

export interface ReorderPolicyUpdateInput {
    actorRole: StockAlertRole;
    policyId: string;
    minimumStock: number;
    safetyStock: number;
    reorderPoint: number;
    standardReorderQuantity: number;
    maximumStock?: number;
    supplierLeadTimeDays?: number;
    averageDailyConsumption?: number;
    enabled: boolean;
    overrideReason?: string;
}

export interface ReorderPolicyBulkUpdateInput {
    actorRole: StockAlertRole;
    category: string;
    locationId: string;
    patch: Partial<Omit<ReorderPolicyUpdateInput, "actorRole" | "policyId">>;
    previewOnly: boolean;
}

export interface PurchaseRequestDraft {
    id: string;
    code: string;
    alertId: string;
    sku: StockSkuSummary;
    location: InventoryLocationSummary;
    supplier: string;
    quantity: number;
    estimatedValue: number;
    status: "DRAFT";
    createdAt: string;
}

export interface StockAlertApi {
    listStockAlerts(query: StockAlertListQuery): Promise<StockAlertListResult>;
    getStockAlert(alertId: string, role: StockAlertRole): Promise<StockAlertDetail>;
    acknowledgeStockAlert(input: StockAlertActionInput): Promise<StockAlertDetail>;
    dismissStockAlert(input: StockAlertActionInput): Promise<StockAlertDetail>;
    resolveStockAlert(input: StockAlertActionInput): Promise<StockAlertDetail>;
    createTransferFromAlert(input: StockAlertActionInput): Promise<StockAlertDetail>;
    createPurchaseRequestFromAlert(input: StockAlertActionInput): Promise<StockAlertDetail>;
    listReorderPolicies(query: ReorderPolicyQuery): Promise<ReorderPolicy[]>;
    updateReorderPolicy(input: ReorderPolicyUpdateInput): Promise<ReorderPolicy>;
    bulkUpdateReorderPolicies(input: ReorderPolicyBulkUpdateInput): Promise<ReorderPolicy[]>;
}
