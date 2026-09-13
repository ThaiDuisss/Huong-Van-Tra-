import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    UserSnapshot
} from "./stock-document.types";

export type ProductionOrderStatus =
    | "DRAFT"
    | "PENDING_APPROVAL"
    | "RELEASED"
    | "IN_PROGRESS"
    | "PARTIALLY_COMPLETED"
    | "COMPLETED"
    | "CANCELLED"
    | "FAILED";

export type ProductionPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";

export type ProductionRole =
    | "ADMIN"
    | "INVENTORY_STAFF"
    | "BRANCH_MANAGER"
    | "ACCOUNTANT"
    | "DIRECTOR"
    | "QC_STAFF";

export interface ProductionOrder {
    id: string;
    code: string;
    status: ProductionOrderStatus;
    location: InventoryLocationSummary;
    title: string;
    plannedStartAt?: string;
    dueAt?: string;
    priority: ProductionPriority;
    owner: UserSnapshot;
    createdBy: UserSnapshot;
    createdAt: string;
    note?: string;
    replenishmentReference?: string;
    outputs: ProductionOrderOutput[];
    materials: ProductionOrderMaterial[];
    completions: ProductionCompletion[];
    auditEvents: InventoryAuditEvent[];
    materialIssueDocumentId?: string;
    materialIssueDocumentCode?: string;
    finishedReceiptDocumentId?: string;
    finishedReceiptDocumentCode?: string;
    hasShortage: boolean;
    progressPercent: number;
    cost: ProductionCostSummary;
}

export interface ProductionOrderSummary {
    id: string;
    code: string;
    status: ProductionOrderStatus;
    location: InventoryLocationSummary;
    title: string;
    dueAt?: string;
    priority: ProductionPriority;
    owner: UserSnapshot;
    outputs: ProductionOrderOutput[];
    plannedOutputQuantity: number;
    hasShortage: boolean;
    progressPercent: number;
    createdBy: UserSnapshot;
    createdAt: string;
}

export interface ProductionOrderOutput {
    finishedSkuId: string;
    skuCode: string;
    skuName: string;
    packageSize: string;
    bomVersionId: string;
    bomVersionLabel: string;
    plannedQuantity: number;
    completedQuantity: number;
    rejectedQuantity: number;
    unit: string;
    plannedExpiryAt?: string;
}

export interface ProductionOrderMaterial {
    materialProductId: string;
    materialProductName: string;
    selectedSkuId: string;
    selectedSkuCode: string;
    specification: string;
    conversionFactor: number;
    requiredBaseQuantity: number;
    requiredSkuQuantity: number;
    availableQuantity: number;
    reservedBaseQuantity: number;
    consumedBaseQuantity: number;
    baseUnit: string;
    wasteTolerancePercent: number;
    preferredSkuCode: string;
    hasAlternative: boolean;
    bomVersionSource: string;
    usedByOutputs: string[];
    allocations: ProductionBatchAllocation[];
}

export interface ProductionBatchAllocation {
    id: string;
    batchCode: string;
    expiryDate: string;
    locationName: string;
    availableQuantity: number;
    allocatedQuantity: number;
    remainingAfterAllocation: number;
    warning?: string;
    blocked: boolean;
}

export interface ProductionCompletion {
    id: string;
    completionNumber: number;
    operationId: string;
    completedAt: string;
    completedBy: UserSnapshot;
    goodQuantity: number;
    rejectedQuantity: number;
    materialConsumedQuantity: number;
    wasteQuantity: number;
    newBatchCode: string;
    manufacturingDate: string;
    expiryDate: string;
    qcNote?: string;
}

export interface ProductionCostSummary {
    materialCost: number;
    packagingCost: number;
    directProductionCost: number;
    allocatedOverhead: number;
    totalProductionCost: number;
    actualGoodOutput: number;
    unitProductionCost: number;
}

export interface FinishedSkuOption {
    id: string;
    skuCode: string;
    skuName: string;
    packageSize: string;
    unit: string;
    activeBomVersionId?: string;
    activeBomVersionLabel?: string;
}

export interface MaterialSkuOption {
    id: string;
    skuCode: string;
    name: string;
    specification: string;
    conversionFactor: number;
}

export interface ProductionOrderOptions {
    locations: InventoryLocationSummary[];
    users: UserSnapshot[];
    finishedSkus: FinishedSkuOption[];
    materialSkus: MaterialSkuOption[];
}

export interface ProductionOrderListQuery {
    page: number;
    size: number;
    code: string;
    locationId: "ALL" | string;
    outputSkuId: "ALL" | string;
    status: "ALL" | ProductionOrderStatus;
    createdFrom: string;
    createdTo: string;
    dueFrom: string;
    dueTo: string;
    createdById: "ALL" | string;
    hasShortage: "ALL" | "YES" | "NO";
}

export interface ProductionOrderListResult {
    content: ProductionOrderSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
    summary: {
        draft: number;
        pendingApproval: number;
        reserved: number;
        inProgress: number;
        partiallyCompleted: number;
        overdue: number;
    };
}

export interface ProductionOrderCreateInput {
    locationId: string;
    title: string;
    plannedStartAt: string;
    dueAt: string;
    priority: ProductionPriority;
    ownerId: string;
    note?: string;
    replenishmentReference?: string;
    outputs: Array<{
        finishedSkuId: string;
        plannedQuantity: number;
        plannedExpiryAt: string;
    }>;
    submit: boolean;
}

export interface ProductionCompletionInput {
    productionOrderId: string;
    idempotencyKey: string;
    goodQuantity: number;
    rejectedQuantity: number;
    actualMaterialConsumption: number;
    wasteQuantity: number;
    varianceReason?: string;
    manufacturingDate: string;
    expiryDate: string;
    qcNote?: string;
    partial: boolean;
}

export interface ProductionActionInput {
    productionOrderId: string;
    note?: string;
}

export interface ProductionOrderApi {
    listProductionOrders(
        query: ProductionOrderListQuery
    ): Promise<ProductionOrderListResult>;
    getProductionOrder(productionOrderId: string): Promise<ProductionOrder>;
    createProductionOrder(
        input: ProductionOrderCreateInput
    ): Promise<ProductionOrder>;
    updateProductionOrder(
        productionOrderId: string,
        input: ProductionOrderCreateInput
    ): Promise<ProductionOrder>;
    submitProductionOrder(input: ProductionActionInput): Promise<ProductionOrder>;
    approveProductionOrder(input: ProductionActionInput): Promise<ProductionOrder>;
    rejectProductionOrder(input: ProductionActionInput): Promise<ProductionOrder>;
    releaseProductionOrder(input: ProductionActionInput): Promise<ProductionOrder>;
    startProductionOrder(input: ProductionActionInput): Promise<ProductionOrder>;
    completeProductionOrder(
        input: ProductionCompletionInput
    ): Promise<ProductionOrder>;
    cancelProductionOrder(input: ProductionActionInput): Promise<ProductionOrder>;
    closeProductionOrderRemainingQuantity(
        input: ProductionActionInput
    ): Promise<ProductionOrder>;
    getProductionOrderOptions(): Promise<ProductionOrderOptions>;
}
