import type { PageResponse } from "./inventory.types";

export type StockDocumentType =
    | "PURCHASE_RECEIPT"
    | "PRODUCTION_RECEIPT"
    | "CUSTOMER_RETURN_RECEIPT"
    | "TRANSFER_RECEIPT"
    | "ADJUSTMENT_IN"
    | "SALE_ISSUE"
    | "PRODUCTION_ISSUE"
    | "CUSTOM_BUNDLE_ISSUE"
    | "TRANSFER_ISSUE"
    | "SUPPLIER_RETURN"
    | "ADJUSTMENT_OUT"
    | "WRITE_OFF";

export type StockDocumentStatus =
    | "DRAFT"
    | "SUBMITTED"
    | "APPROVED"
    | "POSTED"
    | "REJECTED"
    | "CANCELLED"
    | "REVERSED";

export type StockDocumentDirection = "ALL" | "IN" | "OUT";

export type StockDocumentRole =
    | "ADMIN"
    | "INVENTORY_STAFF"
    | "MANAGER"
    | "ACCOUNTANT"
    | "DIRECTOR"
    | "QC_STAFF"
    | "SALES_STAFF";

export type StockDocumentAction =
    | "SUBMIT"
    | "APPROVE"
    | "REJECT"
    | "POST"
    | "CANCEL"
    | "REVERSE";

export type StockDocumentLineStatus =
    | "VALID"
    | "WAITING_BATCH"
    | "QC_HOLD"
    | "POSTED";

export type StockBatchStatus =
    | "AVAILABLE"
    | "NEAR_EXPIRY"
    | "EXPIRED"
    | "DAMAGED"
    | "ALLOCATED";

export interface InventoryLocationSummary {
    id: string;
    code: string;
    name: string;
    branchCode?: string;
    isCentral: boolean;
}

export interface UserSnapshot {
    id: string;
    name: string;
    role: StockDocumentRole;
}

export interface InventoryAuditEvent {
    id: string;
    type:
        | "CREATE_DRAFT"
        | "SUBMIT"
        | "APPROVE"
        | "REJECT"
        | "POST"
        | "CANCEL"
        | "REVERSE"
        | "QC";
    actor: UserSnapshot;
    roleSnapshot: StockDocumentRole;
    happenedAt: string;
    note?: string;
    reason?: string;
}

export interface StockDocumentSummary {
    id: string;
    code: string;
    type: StockDocumentType;
    status: StockDocumentStatus;
    sourceLocation?: InventoryLocationSummary;
    destinationLocation?: InventoryLocationSummary;
    referenceType?: string;
    referenceId?: string;
    referenceCode?: string;
    totalLines: number;
    totalQuantity: number;
    createdBy: UserSnapshot;
    createdAt: string;
    postedAt?: string;
}

export interface StockDocumentDetail extends StockDocumentSummary {
    note?: string;
    reasonCode?: string;
    approvedBy?: UserSnapshot;
    postedBy?: UserSnapshot;
    rejectedBy?: UserSnapshot;
    reversedBy?: UserSnapshot;
    reversedDocumentId?: string;
    reversedDocumentCode?: string;
    correlationId?: string;
    lines: StockDocumentLine[];
    auditEvents: InventoryAuditEvent[];
}

export interface StockDocumentLine {
    id: string;
    skuId: string;
    skuCode: string;
    skuName: string;
    itemType: "PRODUCT" | "INGREDIENT" | "PACKAGING" | "BUNDLE";
    unit: string;
    quantity: number;
    quantityBefore?: number;
    quantityAfter?: number;
    unitCost?: number;
    location?: InventoryLocationSummary;
    status: StockDocumentLineStatus;
    allocations: StockDocumentBatchAllocation[];
}

export interface StockDocumentBatchAllocation {
    id: string;
    skuCode: string;
    batchCode: string;
    supplierLot?: string;
    manufacturedAt?: string;
    expiryDate?: string;
    allocatedQuantity: number;
    batchQuantityBefore?: number;
    batchQuantityAfter?: number;
    status: StockBatchStatus;
}

export interface StockDocumentListQuery {
    page: number;
    size: number;
    documentCode: string;
    referenceCode: string;
    type: "ALL" | StockDocumentType;
    direction: StockDocumentDirection;
    status: "ALL" | StockDocumentStatus;
    locationId: "ALL" | string;
    sourceLocationId: "ALL" | string;
    destinationLocationId: "ALL" | string;
    createdById: "ALL" | string;
    createdFrom: string;
    createdTo: string;
    postedFrom: string;
    postedTo: string;
    hasBatch: "ALL" | "YES" | "NO";
}

export interface StockDocumentListResult
    extends PageResponse<StockDocumentSummary> {
    summary: StockDocumentDashboardSummary;
}

export interface StockDocumentDashboardSummary {
    waitingApproval: number;
    waitingPost: number;
    postedToday: number;
    rejected: number;
    reversedThisMonth: number;
}

export interface StockDocumentOptions {
    locations: InventoryLocationSummary[];
    users: UserSnapshot[];
}

export interface StockDocumentMutationInput {
    documentId: string;
    note?: string;
    reason?: string;
}

export interface StockDocumentApi {
    listStockDocuments(
        query: StockDocumentListQuery
    ): Promise<StockDocumentListResult>;
    getStockDocument(documentId: string): Promise<StockDocumentDetail>;
    submitStockDocument(
        input: StockDocumentMutationInput
    ): Promise<StockDocumentDetail>;
    approveStockDocument(
        input: StockDocumentMutationInput
    ): Promise<StockDocumentDetail>;
    rejectStockDocument(
        input: StockDocumentMutationInput
    ): Promise<StockDocumentDetail>;
    postStockDocument(
        input: StockDocumentMutationInput
    ): Promise<StockDocumentDetail>;
    cancelStockDocument(
        input: StockDocumentMutationInput
    ): Promise<StockDocumentDetail>;
    reverseStockDocument(
        input: StockDocumentMutationInput
    ): Promise<StockDocumentDetail>;
    getStockDocumentOptions(): Promise<StockDocumentOptions>;
}

export type AppRole = StockDocumentRole;
