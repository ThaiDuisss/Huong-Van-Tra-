import type {
    InventoryAuditEvent,
    InventoryLocationSummary,
    UserSnapshot
} from "./stock-document.types";
import type { RoleKey } from "../../../auth/roles";

export type QualityBatchStatus =
    | "AVAILABLE"
    | "NEAR_EXPIRY"
    | "EXPIRED"
    | "QUARANTINE"
    | "DAMAGED"
    | "BLOCKED"
    | "DISPOSED";

/** Vai trò người thao tác — dùng role chuẩn của hệ thống. */
export type QualityRole = RoleKey;

export type QualitySourceType =
    | "GOODS_RECEIPT_QC"
    | "CUSTOMER_RETURN_QC"
    | "BATCH_INSPECTION"
    | "EXPIRY_SCAN"
    | "TRANSFER_RECEIPT";

export type QualitySeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type QualityCaseStatus = "OPEN" | "IN_REVIEW" | "RESOLVED" | "CANCELLED";
export type QualityRecommendedAction =
    | "QUARANTINE"
    | "RELEASE"
    | "SUPPLIER_RETURN"
    | "DISPOSAL"
    | "BLOCK";

export type DisposalStatus =
    | "PENDING_APPROVAL"
    | "DRAFT"
    | "SUBMITTED"
    | "APPROVED"
    | "SCHEDULED"
    | "COMPLETED"
    | "REJECTED"
    | "CANCELLED";

export type DisposalMethod = "DESTROY" | "RETURN_TO_VENDOR" | "OTHER";
export type QualityTab =
    | "NEAR_EXPIRY"
    | "EXPIRED"
    | "QUARANTINE"
    | "DAMAGED"
    | "DISPOSAL_PENDING"
    | "DISPOSED";

export interface QualityBatchSummary {
    id: string;
    batchCode: string;
    skuCode: string;
    skuName: string;
    productType: string;
    supplier: string;
    supplierLot: string;
    location: InventoryLocationSummary;
    onHandQuantity: number;
    reservedQuantity: number;
    blockedQuantity: number;
    availableQuantity: number;
    damagedQuantity: number;
    manufacturingDate: string;
    expiryDate: string;
    status: QualityBatchStatus;
    reason?: string;
    unit: string;
    unitCost: number;
    estimatedValue: number;
    quarantineSince?: string;
    qcResult?: "PENDING" | "PASSED" | "FAILED";
}

export interface QualityCase {
    id: string;
    code: string;
    sourceType: QualitySourceType;
    referenceCode: string;
    batchId: string;
    batchCode: string;
    quantityAffected: number;
    reason: string;
    severity: QualitySeverity;
    evidence: string[];
    recommendedAction: QualityRecommendedAction;
    assignee: UserSnapshot;
    slaDueAt: string;
    status: QualityCaseStatus;
}

export interface BatchQualityDetail extends QualityBatchSummary {
    receiptId: string;
    receiptCode: string;
    reservations: Array<{ id: string; referenceCode: string; quantity: number }>;
    locationBalances: Array<{ location: InventoryLocationSummary; quantity: number; blockedQuantity: number }>;
    movements: Array<{ id: string; happenedAt: string; type: string; quantity: number; referenceCode: string }>;
    qualityEvents: QualityCase[];
    documentLinks: Array<{ id: string; code: string; type: string }>;
    auditEvents: InventoryAuditEvent[];
}

export interface QualityBatchQuery {
    tab: QualityTab;
    locationId: "ALL" | string;
    sku: string;
    productType: "ALL" | string;
    batchCode: string;
    supplier: string;
    expiryFrom: string;
    expiryTo: string;
    status: "ALL" | QualityBatchStatus;
    reason: string;
    minValue: number;
    quarantineDays: number;
}

export interface QualityDashboardResult {
    content: QualityBatchSummary[];
    summary: {
        expiry7Days: number;
        expiry30Days: number;
        expiredUnhandled: number;
        quarantineOverSla: number;
        damagedValue: number;
        pendingDisposalApprovals: number;
    };
}

export interface DisposalLine {
    id: string;
    batchId: string;
    batchCode: string;
    skuCode: string;
    skuName: string;
    quantity: number;
    actualDisposedQuantity: number;
    reservedQuantity: number;
    unit: string;
    unitCost: number;
    estimatedValue: number;
    reason: string;
    evidence: string[];
}

export interface DisposalRequest {
    id: string;
    sourceType?: string;
    code: string;
    sourceInspectionId?: string;
    inspectionCode?: string;
    returnCode?: string;
    location: InventoryLocationSummary;
    reason: string;
    method: DisposalMethod;
    scheduledAt: string;
    witnesses: string[];
    note?: string;
    status: DisposalStatus;
    requestedBy: UserSnapshot;
    requestedAt: string;
    approvedBy?: UserSnapshot;
    approvedAt?: string;
    rejectedReason?: string;
    completedAt?: string;
    completedBy?: UserSnapshot;
    certificate?: string;
    evidenceAfterDisposal?: string;
    stockDocumentId?: string;
    stockDocumentCode?: string;
    lines: DisposalLine[];
    auditEvents: InventoryAuditEvent[];
}

export interface DisposalListQuery {
    fromDate?: string;
    toDate?: string;
    page: number;
    size: number;
    status: "ALL" | "PENDING_APPROVAL" | "APPROVED";
    keyword: string;
}

export interface DisposalStatusCounts {
    [status: string]: number;
    ALL: number;
    PENDING_APPROVAL: number;
    APPROVED: number;
}

export interface DisposalListResult {
    content: DisposalRequest[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface DisposalCreateInput {
    locationId: string;
    reason: string;
    method: DisposalMethod;
    scheduledAt: string;
    witnesses: string[];
    note?: string;
    lines: Array<{ batchId: string; quantity: number; reason: string; evidence: string[] }>;
}

export interface QualityActionInput {
    batchId: string;
    note?: string;
    adminOverride?: boolean;
}

export interface DisposalActionInput {
    disposalId: string;
    reason?: string;
}

export interface DisposalCompleteInput extends DisposalActionInput {
    completedAt: string;
    completedByName: string;
    witnesses: string[];
    certificate: string;
    evidenceAfterDisposal: string;
    lines: Array<{ lineId: string; actualDisposedQuantity: number }>;
}

export interface QualityApi {
    listQualityBatches(query: QualityBatchQuery): Promise<QualityDashboardResult>;
    getBatchQualityDetail(batchId: string): Promise<BatchQualityDetail>;
    createQualityCase(input: Omit<QualityCase, "id" | "code" | "status">): Promise<BatchQualityDetail>;
    updateQualityCase(caseId: string, patch: Partial<QualityCase>): Promise<BatchQualityDetail>;
    moveBatchToQuarantine(input: QualityActionInput): Promise<BatchQualityDetail>;
    releaseBatchFromQuarantine(input: QualityActionInput): Promise<BatchQualityDetail>;
    markBatchDamaged(input: QualityActionInput): Promise<BatchQualityDetail>;
    blockBatch(input: QualityActionInput): Promise<BatchQualityDetail>;
    unblockBatch(input: QualityActionInput): Promise<BatchQualityDetail>;
    createDisposalRequest(input: DisposalCreateInput): Promise<DisposalRequest>;
    listDisposalRequests(query: DisposalListQuery): Promise<DisposalListResult>;
    getDisposalStatusCounts(keyword?: string, fromDate?: string, toDate?: string): Promise<DisposalStatusCounts>;
    getDisposalRequest(disposalId: string): Promise<DisposalRequest>;
    approveDisposalRequest(input: DisposalActionInput): Promise<DisposalRequest>;
    rejectDisposalRequest(input: DisposalActionInput): Promise<DisposalRequest>;
    completeDisposalRequest(input: DisposalCompleteInput): Promise<DisposalRequest>;
}

export type AppRole = QualityRole;
