import type {
    InventoryAuditEvent,
    UserSnapshot
} from "./stock-document.types";

export type SupplierReturnStatus =
    | "DRAFT"
    | "SUBMITTED"
    | "APPROVED"
    | "PREPARING"
    | "SHIPPED"
    | "CREDIT_PENDING"
    | "REPLACEMENT_PENDING"
    | "COMPLETED"
    | "REJECTED"
    | "CANCELLED";

export type SupplierReturnRole =
    | "INVENTORY_STAFF"
    | "QC_STAFF"
    | "ACCOUNTANT"
    | "ADMIN"
    | "DIRECTOR";

export type SupplierReturnResolution = "CREDIT_NOTE" | "REPLACEMENT" | "REFUND";
export type SupplierReturnReason =
    | "SUP-01"
    | "SUP-02"
    | "SUP-03"
    | "SUP-04"
    | "SUP-05"
    | "SUP-06"
    | "SUP-99";

export interface SupplierSummary {
    id: string;
    code: string;
    name: string;
}

export interface SupplierReturnBatchOption {
    id: string;
    supplierId: string;
    receiptId: string;
    receiptCode: string;
    supplierInvoiceCode: string;
    skuId: string;
    skuCode: string;
    skuName: string;
    batchCode: string;
    receivedQuantity: number;
    currentAvailableQuantity: number;
    currentQuarantineQuantity: number;
    consumedQuantity: number;
    unit: string;
    unitCost: number;
    qcResult: "PASSED" | "FAILED" | "PARTIALLY_PASSED";
}

export interface SupplierReturnLine {
    id: string;
    skuId: string;
    skuCode: string;
    skuName: string;
    batchId: string;
    batchCode: string;
    receivedQuantity: number;
    currentAvailableQuantity: number;
    currentQuarantineQuantity: number;
    returnableQuantity: number;
    returnQuantity: number;
    preparedQuantity: number;
    unit: string;
    unitCost: number;
    estimatedValue: number;
    qcResult: "PASSED" | "FAILED" | "PARTIALLY_PASSED";
    reason: SupplierReturnReason;
    evidence: string[];
}

export interface SupplierReturnSummary {
    id: string;
    code: string;
    supplier: SupplierSummary;
    receiptCode: string;
    supplierInvoiceCode: string;
    totalSku: number;
    totalQuantity: number;
    totalValue: number;
    status: SupplierReturnStatus;
    createdAt: string;
    createdBy: UserSnapshot;
    resolutionRequested: SupplierReturnResolution;
    creditNoteCode?: string;
    replacementReceiptCode?: string;
}

export interface SupplierReturnDetail extends SupplierReturnSummary {
    receiptId: string;
    reason: SupplierReturnReason;
    pickupMethod: string;
    note?: string;
    approvedBy?: UserSnapshot;
    approvedAt?: string;
    rejectedReason?: string;
    packageCount?: number;
    carrier?: string;
    trackingCode?: string;
    shippedAt?: string;
    supplierReceiver?: string;
    shippingAttachment?: string;
    stockDocumentId?: string;
    stockDocumentCode?: string;
    creditAmount?: number;
    creditNoteDate?: string;
    expectedReplacementDate?: string;
    settlementNote?: string;
    settlementAttachment?: string;
    lines: SupplierReturnLine[];
    auditEvents: InventoryAuditEvent[];
}

export interface SupplierReturnListQuery {
    page: number;
    size: number;
    code: string;
    supplierId: "ALL" | string;
    receiptCode: string;
    batchCode: string;
    status: "ALL" | SupplierReturnStatus;
    reason: "ALL" | SupplierReturnReason;
    createdFrom: string;
    createdTo: string;
}

export interface SupplierReturnListResult {
    content: SupplierReturnSummary[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    summary: {
        submitted: number;
        preparing: number;
        shipped: number;
        creditPending: number;
        replacementPending: number;
    };
}

export interface SupplierReturnOptions {
    suppliers: SupplierSummary[];
    batches: SupplierReturnBatchOption[];
    users: UserSnapshot[];
}

export interface SupplierReturnCreateInput {
    supplierId: string;
    receiptId: string;
    supplierInvoiceCode: string;
    reason: SupplierReturnReason;
    resolutionRequested: SupplierReturnResolution;
    pickupMethod: string;
    note?: string;
    submit: boolean;
    lines: Array<{
        batchId: string;
        returnQuantity: number;
        reason: SupplierReturnReason;
        evidence: string[];
    }>;
}

export interface SupplierReturnActionInput {
    supplierReturnId: string;
    note?: string;
}

export interface SupplierReturnRejectInput extends SupplierReturnActionInput {
    reason: string;
}

export interface SupplierReturnPrepareInput extends SupplierReturnActionInput {
    packageCount: number;
    lines: Array<{ lineId: string; preparedQuantity: number }>;
}

export interface SupplierReturnShipInput extends SupplierReturnActionInput {
    carrier: string;
    trackingCode: string;
    shippedAt: string;
    supplierReceiver: string;
    shippingAttachment?: string;
}

export interface SupplierReturnSettlementInput extends SupplierReturnActionInput {
    creditNoteCode?: string;
    creditAmount?: number;
    creditNoteDate?: string;
    replacementReceiptCode?: string;
    expectedReplacementDate?: string;
    settlementNote?: string;
    settlementAttachment?: string;
}

export interface SupplierReturnApi {
    listSupplierReturns(query: SupplierReturnListQuery): Promise<SupplierReturnListResult>;
    getSupplierReturn(supplierReturnId: string): Promise<SupplierReturnDetail>;
    createSupplierReturn(input: SupplierReturnCreateInput): Promise<SupplierReturnDetail>;
    submitSupplierReturn(input: SupplierReturnActionInput): Promise<SupplierReturnDetail>;
    approveSupplierReturn(input: SupplierReturnActionInput): Promise<SupplierReturnDetail>;
    rejectSupplierReturn(input: SupplierReturnRejectInput): Promise<SupplierReturnDetail>;
    prepareSupplierReturn(input: SupplierReturnPrepareInput): Promise<SupplierReturnDetail>;
    shipSupplierReturn(input: SupplierReturnShipInput): Promise<SupplierReturnDetail>;
    updateSupplierReturnSettlement(input: SupplierReturnSettlementInput): Promise<SupplierReturnDetail>;
    completeSupplierReturn(input: SupplierReturnActionInput): Promise<SupplierReturnDetail>;
    cancelSupplierReturn(input: SupplierReturnActionInput): Promise<SupplierReturnDetail>;
    getSupplierReturnOptions(): Promise<SupplierReturnOptions>;
}

export type AppRole = SupplierReturnRole;
