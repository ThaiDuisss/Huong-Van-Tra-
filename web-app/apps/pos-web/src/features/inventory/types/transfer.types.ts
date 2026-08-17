import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    UserSnapshot
} from "./stock-document.types";

export type TransferStatus =
    | "DRAFT"
    | "REQUESTED"
    | "APPROVED"
    | "RESERVED"
    | "PICKING"
    | "SHIPPED"
    | "PARTIALLY_RECEIVED"
    | "RECEIVED"
    | "REJECTED"
    | "CANCELLED";

export type TransferRole =
    | "BRANCH_MANAGER"
    | "WAREHOUSE"
    | "ADMIN"
    | "DIRECTOR"
    | "ACCOUNTANT"
    | "SALES_STAFF";

export type TransferPriority = "LOW" | "NORMAL" | "HIGH" | "URGENT";
export type TransferListTab =
    | "ALL"
    | "REQUESTED"
    | "PREPARING"
    | "IN_TRANSIT"
    | "AWAITING_RECEIVE"
    | "DISCREPANCY"
    | "COMPLETED";

export type TransferDiscrepancyType =
    | "SHORTAGE"
    | "DAMAGE"
    | "EXTRA"
    | "WRONG_SKU"
    | "WRONG_BATCH";

export type TransferDiscrepancyResolutionStatus =
    | "OPEN"
    | "INVESTIGATING"
    | "RESOLVED";

export interface TransferSkuOption {
    id: string;
    skuCode: string;
    name: string;
    unit: string;
    category: string;
    sourceAvailableQuantity: number;
    destinationAvailableQuantity: number;
    destinationMinimumStock: number;
    unitCost: number;
    batches: TransferBatchOption[];
}

export interface TransferBatchOption {
    id: string;
    batchCode: string;
    expiryDate: string;
    availableQuantity: number;
    blocked: boolean;
    expired: boolean;
}

export interface TransferLine {
    id: string;
    skuId: string;
    skuCode: string;
    skuName: string;
    unit: string;
    requestedQuantity: number;
    approvedQuantity: number;
    sourceAvailableQuantity: number;
    destinationAvailableQuantity: number;
    destinationMinimumStock: number;
    shippedQuantity: number;
    receivedGoodQuantity: number;
    damagedQuantity: number;
    missingQuantity: number;
    extraQuantity: number;
    note?: string;
    allocations: TransferBatchAllocation[];
}

export interface TransferBatchAllocation {
    id: string;
    batchId: string;
    batchCode: string;
    expiryDate: string;
    reservedQuantity: number;
    pickedQuantity: number;
    picked: boolean;
}

export interface TransferShipment {
    packageCount: number;
    carrierName: string;
    vehiclePlate: string;
    internalTrackingCode: string;
    expectedArrivalAt: string;
    shippedAt: string;
}

export interface TransferDiscrepancy {
    id: string;
    type: TransferDiscrepancyType;
    lineId: string;
    quantity: number;
    evidence?: string;
    responsibleParty: string;
    resolutionStatus: TransferDiscrepancyResolutionStatus;
    adminNote?: string;
}

export interface TransferSummary {
    id: string;
    code: string;
    sourceLocation: InventoryLocationSummary;
    destinationLocation: InventoryLocationSummary;
    totalSku: number;
    totalQuantity: number;
    status: TransferStatus;
    requestedAt: string;
    expectedReceiveAt: string;
    requestedBy: UserSnapshot;
    hasDiscrepancy: boolean;
    totalValue: number;
}

export interface TransferDetail extends TransferSummary {
    priority: TransferPriority;
    reason: string;
    lines: TransferLine[];
    shipment?: TransferShipment;
    discrepancies: TransferDiscrepancy[];
    issueDocumentId?: string;
    issueDocumentCode?: string;
    receiptDocumentId?: string;
    receiptDocumentCode?: string;
    auditEvents: InventoryAuditEvent[];
}

export interface TransferListQuery {
    page: number;
    size: number;
    tab: TransferListTab;
    code: string;
    sourceLocationId: "ALL" | string;
    destinationLocationId: "ALL" | string;
    status: "ALL" | TransferStatus;
    requestedById: "ALL" | string;
    requestedFrom: string;
    requestedTo: string;
    expectedFrom: string;
    expectedTo: string;
    hasDiscrepancy: "ALL" | "YES" | "NO";
}

export interface TransferListResult {
    content: TransferSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    first: boolean;
    last: boolean;
    summary: {
        newRequests: number;
        inTransit: number;
        overdue: number;
        discrepancy: number;
    };
}

export interface TransferOptions {
    locations: InventoryLocationSummary[];
    users: UserSnapshot[];
    skus: TransferSkuOption[];
}

export interface TransferCreateInput {
    actorRole: TransferRole;
    sourceLocationId: string;
    destinationLocationId: string;
    expectedReceiveAt: string;
    priority: TransferPriority;
    reason: string;
    submit: boolean;
    lines: Array<{
        skuId: string;
        requestedQuantity: number;
        note?: string;
    }>;
}

export interface TransferActionInput {
    actorRole: TransferRole;
    transferId: string;
    note?: string;
}

export interface TransferApproveInput extends TransferActionInput {
    lines: Array<{
        lineId: string;
        approvedQuantity: number;
    }>;
}

export interface TransferPickingInput extends TransferActionInput {
    scannedBatchCode?: string;
    lines: Array<{
        lineId: string;
        allocations: Array<{
            allocationId: string;
            picked: boolean;
            pickedQuantity: number;
        }>;
    }>;
}

export interface TransferShipInput extends TransferActionInput {
    packageCount: number;
    carrierName: string;
    vehiclePlate: string;
    internalTrackingCode: string;
    expectedArrivalAt: string;
}

export interface TransferReceiveInput extends TransferActionInput {
    lines: Array<{
        lineId: string;
        receivedGoodQuantity: number;
        damagedQuantity: number;
        missingQuantity: number;
        extraQuantity: number;
        batchCode: string;
        evidence?: string;
        note?: string;
    }>;
}

export interface TransferDiscrepancyResolveInput extends TransferActionInput {
    discrepancyId: string;
    resolutionStatus: TransferDiscrepancyResolutionStatus;
    adminNote: string;
}

export interface TransferApi {
    listTransfers(query: TransferListQuery): Promise<TransferListResult>;
    getTransfer(transferId: string): Promise<TransferDetail>;
    createTransfer(input: TransferCreateInput): Promise<TransferDetail>;
    submitTransfer(input: TransferActionInput): Promise<TransferDetail>;
    approveTransfer(input: TransferApproveInput): Promise<TransferDetail>;
    rejectTransfer(input: TransferActionInput): Promise<TransferDetail>;
    reserveTransfer(input: TransferActionInput): Promise<TransferDetail>;
    startPicking(input: TransferActionInput): Promise<TransferDetail>;
    confirmTransferPicking(input: TransferPickingInput): Promise<TransferDetail>;
    shipTransfer(input: TransferShipInput): Promise<TransferDetail>;
    receiveTransfer(input: TransferReceiveInput): Promise<TransferDetail>;
    resolveTransferDiscrepancy(
        input: TransferDiscrepancyResolveInput
    ): Promise<TransferDetail>;
    cancelTransfer(input: TransferActionInput): Promise<TransferDetail>;
    getTransferOptions(): Promise<TransferOptions>;
}
