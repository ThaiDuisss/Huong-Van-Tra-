import type { EntityId, PageResponse } from "./inventory.types";

export type InventoryRole =
    | "INVENTORY_MANAGER"
    | "WAREHOUSE_STAFF"
    | "PRODUCTION_STAFF"
    | "BRANCH_STAFF"
    | "AUDITOR";

export type InventoryWorkspaceId =
    | "roles"
    | "documents"
    | "raw-material-receipts"
    | "production-orders"
    | "tea-bundles"
    | "branch-transfers"
    | "stock-counts"
    | "customer-returns"
    | "supplier-returns"
    | "waste-handling"
    | "replenishment-alerts";

export type InventoryRecordStatus =
    | "DRAFT"
    | "PENDING_APPROVAL"
    | "APPROVED"
    | "IN_PROGRESS"
    | "COMPLETED"
    | "REJECTED"
    | "CANCELLED"
    | "WARNING"
    | "BLOCKED";

export type InventoryDocumentType =
    | "STOCK_RECEIPT"
    | "PRODUCTION_ORDER"
    | "BUNDLE_ORDER"
    | "TRANSFER_ORDER"
    | "STOCK_COUNT"
    | "CUSTOMER_RETURN"
    | "SUPPLIER_RETURN"
    | "DISPOSAL"
    | "REPLENISHMENT";

export type InventoryActionType =
    | "CREATE"
    | "APPROVE"
    | "COMPLETE"
    | "CANCEL"
    | "EXPORT";

export interface InventoryWorkspaceConfig {
    id: InventoryWorkspaceId;
    title: string;
    description: string;
    createLabel?: string;
    documentType?: InventoryDocumentType;
    allowedActions: Partial<Record<InventoryActionType, InventoryRole[]>>;
}

export interface InventoryManagementQuery {
    page: number;
    size: number;
    search: string;
    status: "ALL" | InventoryRecordStatus;
    locationId: "ALL" | string;
    sortBy: InventorySortField;
    sortDirection: "asc" | "desc";
}

export type InventorySortField =
    | "updatedAt"
    | "code"
    | "quantity"
    | "value";

export interface InventoryLocationOption {
    id: string;
    name: string;
    type: "CENTRAL" | "BRANCH" | "DAMAGED";
}

export interface InventorySkuOption {
    id: string;
    sku: string;
    name: string;
    unit: string;
}

export interface InventoryBatchOption {
    id: string;
    code: string;
    skuId: string;
    expiryDate: string;
    availableQuantity: number;
}

export interface InventoryActivity {
    id: string;
    at: string;
    actor: string;
    title: string;
    description: string;
}

export interface InventoryApprovalStep {
    id: string;
    role: InventoryRole;
    label: string;
    status: "WAITING" | "APPROVED" | "REJECTED";
    actor?: string;
    at?: string;
}

export interface InventoryBatchAllocation {
    batchId: string;
    batchCode: string;
    expiryDate: string;
    quantity: number;
}

export interface InventoryManagementRecord {
    id: EntityId;
    workspaceId: InventoryWorkspaceId;
    code: string;
    title: string;
    description: string;
    status: InventoryRecordStatus;
    documentType: InventoryDocumentType;
    locationId: string;
    locationName: string;
    targetLocationName?: string;
    sku: string;
    itemName: string;
    unit: string;
    quantity: number;
    value: number;
    reasonCode?: string;
    owner: string;
    updatedAt: string;
    dueAt?: string;
    referenceCode?: string;
    batchAllocations: InventoryBatchAllocation[];
    activities: InventoryActivity[];
    approvals: InventoryApprovalStep[];
    evidenceCount: number;
    canEdit: boolean;
}

export interface InventoryManagementListResult
    extends PageResponse<InventoryManagementRecord> {
    summary: InventoryManagementSummary;
}

export interface InventoryManagementSummary {
    totalRecords: number;
    pendingApproval: number;
    warningRecords: number;
    totalQuantity: number;
    totalValue: number;
}

export interface InventoryCreateDraftInput {
    workspaceId: InventoryWorkspaceId;
    title: string;
    skuId: string;
    locationId: string;
    quantity: number;
    unit: string;
    reasonCode: string;
    note: string;
}

export interface InventoryActionInput {
    recordId: EntityId;
    action: InventoryActionType;
    role: InventoryRole;
    note: string;
}

export interface InventoryManagementApi {
    list(
        workspaceId: InventoryWorkspaceId,
        query: InventoryManagementQuery
    ): Promise<InventoryManagementListResult>;
    createDraft(
        input: InventoryCreateDraftInput
    ): Promise<InventoryManagementRecord>;
    runAction(input: InventoryActionInput): Promise<InventoryManagementRecord>;
    getOptions(): Promise<{
        locations: InventoryLocationOption[];
        skus: InventorySkuOption[];
        batches: InventoryBatchOption[];
    }>;
}
